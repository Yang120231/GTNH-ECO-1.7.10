package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOForcedCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Frozen pattern contracts captured on the owning server thread; revision reads stay lock-free for the planner. */
public final class CraftingServiceView
    implements ICraftingService, ECOCraftingProviderRevision, ECOCraftingOutputRouter {

    private final ICraftingGrid crafting;
    private final World world;
    private final NativePatternTrackingCache tracking;
    private final long revision;
    private final long catalogRevision;
    private final long recipeGeneration;
    private final Map<ICraftingPatternDetails, NativePatternDetails> patterns = new IdentityHashMap<>();
    private final Map<AEKey, List<IPatternDetails>> byOutput;
    private final Set<AEKey> craftables;
    private final Set<AEKey> emitters;

    CraftingServiceView(IGrid grid, World world, NativePatternTrackingCache tracking,
        NativeSubstitutionIndex substitutes, long recipeGeneration) {
        crafting = grid.getCache(ICraftingGrid.class);
        this.tracking = tracking;
        this.world = world;
        this.revision = tracking.revision();
        this.catalogRevision = tracking.catalogRevision();
        this.recipeGeneration = recipeGeneration;
        // The source compiler runs off-thread. Resolve every native pattern/recipe query here on the server thread.
        Map<AEKey, List<IPatternDetails>> index = new java.util.LinkedHashMap<>();
        for (var entry : crafting.getCraftingMultiPatterns()
            .entrySet()) {
            var key = AEKey.fromNative(entry.getKey());
            if (key == null) continue;
            var captured = new ArrayList<IPatternDetails>();
            for (var nativePattern : entry.getValue()) {
                var details = capture(nativePattern, substitutes, world);
                if (details != null) captured.add(details);
            }
            index.put(key, Collections.unmodifiableList(captured));
        }
        byOutput = Collections.unmodifiableMap(index);
        craftables = byOutput.keySet();
        Set<AEKey> emitted = new LinkedHashSet<>();
        for (var stack : crafting.getEmitableItems()) {
            var key = AEKey.fromNative(stack);
            if (key != null) emitted.add(key);
        }
        emitters = Collections.unmodifiableSet(emitted);
    }

    /** Native AE2 declines unrepresentable patterns (e.g. zero-sized stacks) instead of failing the grid tick. */
    private NativePatternDetails capture(ICraftingPatternDetails pattern, NativeSubstitutionIndex substitutes,
        World world) {
        NativePatternDetails details = patterns.get(pattern);
        if (details != null) return details;
        try {
            details = NativePatternDetails.hasTunnelInputs(pattern.getAEInputs()) && !pattern.isCraftable()
                ? NativePatternDetails.capture(pattern, world, substitutes, (CraftingGridCache) crafting)
                : substitutes.details(pattern);
        } catch (IllegalArgumentException rejected) {
            return null;
        }
        patterns.put(pattern, details);
        return details;
    }

    Set<ICraftingPatternDetails> nativePatterns() {
        return patterns.keySet();
    }

    @Override
    public List<IPatternDetails> getCraftingFor(AEKey key) {
        var order = TemporaryCraftingOrder.decode(key);
        if (order != null) return Collections.singletonList(order);
        return byOutput.getOrDefault(key, Collections.emptyList());
    }

    @Override
    public Set<AEKey> getCraftables(AEKeyFilter filter) {
        var result = new LinkedHashSet<AEKey>();
        for (var key : craftables) if (filter.matches(key)) result.add(key);
        return result;
    }

    @Override
    public boolean canEmitFor(AEKey key) {
        return emitters.contains(key);
    }

    boolean owns(NativePatternDetails details) {
        return patterns.get(details.nativeDetails()) == details;
    }

    public boolean belongsTo(ICraftingService service) {
        return tracking.live(world) == service && neoecoae$isProviderSnapshotStable();
    }

    @Override
    public List<ICraftingProvider> getProviders(IPatternDetails pattern) {
        return isCatalogCurrent() ? tracking.live(world)
            .getProviders(pattern) : Collections.emptyList();
    }

    @Override
    public void addLink(CraftingLink link) {
        tracking.live(world)
            .addLink(link);
    }

    @Override
    public long insertIntoCpus(AEKey key, long amount, appeng.api.config.Actionable mode) {
        return tracking.live(world)
            .insertIntoCpus(key, amount, mode);
    }

    @Override
    public long neoecoae$insertIntoCpuForJob(java.util.UUID job, AEKey key, long amount,
        appeng.api.config.Actionable mode) {
        return tracking.live(world)
            .neoecoae$insertIntoCpuForJob(job, key, amount, mode);
    }

    @Override
    public java.math.BigInteger neoecoae$insertExactIntoCpuForJob(java.util.UUID job, AEKey key,
        java.math.BigInteger amount, appeng.api.config.Actionable mode) {
        return tracking.live(world)
            .neoecoae$insertExactIntoCpuForJob(job, key, amount, mode);
    }

    @Override
    public long neoecoae$getProviderRevision() {
        return revision;
    }

    @Override
    public boolean neoecoae$isProviderSnapshotStable() {
        return isCatalogCurrent() && revision == tracking.revision();
    }

    public boolean isCatalogCurrent() {
        return catalogRevision == tracking.catalogRevision() && recipeGeneration == PatternRuntime.reloadGeneration();
    }

    /** Recheck the frozen task vector against the live publication, not the original planning snapshot. */
    private boolean supportsPlan(ICraftingPlan plan, boolean directOnly, NativeLiveCraftingService live) {
        ICraftingPlan base = plan instanceof ECOForcedCraftingPlan forced ? forced.delegate() : plan;
        Set<IPatternDetails> tasks;
        if (base.simulation()) {
            // Missing/partial reports depend on absent producers. Only an exact, complete big order can reuse
            // its simulation shell: AE2's long projection contains none of its actual tasks or emissions.
            var exact = base instanceof ECOCraftingPlanDiagnostics diagnostics
                ? diagnostics.neoecoae$getPlanningResult()
                : null;
            if (directOnly || exact == null
                || exact.plan() != base
                || exact.status() != PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                || !exact.exactMissingItems()
                    .isEmpty())
                return false;
            // This view is the original planning snapshot; validate emissions against the live publication.
            for (var entry : exact.exactEmittedItems()
                .entrySet())
                if (entry.getValue()
                    .signum() > 0 && !live.canEmitFor(entry.getKey())) return false;
            tasks = exact.exactPatternTimes()
                .keySet();
            if (tasks.isEmpty() && exact.exactEmittedItems()
                .isEmpty()) return false;
        } else {
            // A force-start stock shortfall is awaited, not a requirement for a physical emitter.
            for (var entry : base.emittedItems())
                if (entry.getLongValue() > 0 && !live.canEmitFor(entry.getKey())) return false;
            tasks = base.patternTimes()
                .keySet();
        }
        for (var pattern : tasks) {
            if (!directOnly && pattern instanceof TemporaryCraftingOrder.Pattern) continue;
            if (!(pattern instanceof NativePatternDetails) || live.getProviders(pattern, directOnly)
                .isEmpty()) return false;
        }
        return true;
    }

    public long publication() {
        return catalogRevision;
    }

    public boolean isPublicationCurrent(long stamp) {
        return stamp >= 0 && stamp == tracking.catalogRevision()
            && recipeGeneration == PatternRuntime.reloadGeneration();
    }

    /** Return a validation stamp, never build a new planning snapshot just to check an old job. */
    public long validatePlan(World world, ICraftingPlan plan, boolean directOnly) {
        return validatePlan(world, plan, directOnly, catalogRevision);
    }

    public long validatePlan(World world, ICraftingPlan plan, boolean directOnly, long lastValidated) {
        if (plan == null || world == null) return -1;
        try {
            // Reading the current stamp may itself flush pending native provider callbacks.
            if (isPublicationCurrent(lastValidated)) return lastValidated;
            if (!ServerTasks.isServerThread() || recipeGeneration != PatternRuntime.reloadGeneration()) return -1;
            long before = tracking.liveRevision();
            return supportsPlan(plan, directOnly, tracking.live(world)) && isPublicationCurrent(before) ? before : -1;
        } catch (RuntimeException | LinkageError unavailable) {
            return -1;
        }
    }
}
