package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Frozen pattern contracts captured on the owning server thread; revision reads stay lock-free for the planner. */
public final class CraftingServiceView
    implements ICraftingService, ECOCraftingProviderRevision, ECOCraftingOutputRouter {

    private static final Set<String> REJECTED_TYPES = ConcurrentHashMap.newKeySet();

    private final ICraftingGrid crafting;
    private final NativePatternTrackingCache tracking;
    private final long revision;
    private final long catalogRevision;
    private final long recipeGeneration;
    private final Map<ICraftingPatternDetails, NativePatternDetails> patterns = new IdentityHashMap<>();
    private final Map<AEKey, List<IPatternDetails>> byOutput;
    private final Map<AEItemKey, List<NativePatternDetails>> processingByDefinition = new java.util.HashMap<>();
    private final Set<AEKey> craftables;
    private final Set<AEKey> emitters;
    private final Map<appeng.api.networking.crafting.ICraftingMedium, Map<IPatternDetails, ICraftingProvider>> providers = new IdentityHashMap<>();

    CraftingServiceView(IGrid grid, World world, Collection<AEKey> candidates, NativePatternTrackingCache tracking,
        NativeSubstitutionIndex substitutes, long recipeGeneration) {
        crafting = grid.getCache(ICraftingGrid.class);
        this.tracking = tracking;
        this.revision = tracking.revision();
        this.catalogRevision = tracking.catalogRevision();
        this.recipeGeneration = recipeGeneration;
        // The source compiler runs off-thread. Resolve every native pattern/recipe query here on the server thread.
        Set<AEKey> keys = new LinkedHashSet<>(candidates);
        Set<AEKey> outputs = new LinkedHashSet<>();
        // Match GTNH CraftingContext#getPrecisePatternsFor: native getCraftingFor(IAEStack) only answers item stacks,
        // so fluid (and other generic) producers must be read from the multi-type catalog directly.
        var catalog = crafting.getCraftingMultiPatterns();
        for (var entry : catalog.entrySet()) {
            AEKey key = AEKey.fromNative(entry.getKey());
            if (key == null) continue;
            outputs.add(key);
            keys.add(key);
            for (var pattern : entry.getValue()) {
                for (var input : pattern.getAEInputs()) if (input != null) keys.add(AEKey.fromNative(input));
                for (var output : pattern.getAEOutputs()) if (output != null) keys.add(AEKey.fromNative(output));
            }
        }
        keys.remove(null);
        Map<AEKey, List<IPatternDetails>> index = new java.util.LinkedHashMap<>();
        Set<AEKey> emitted = new LinkedHashSet<>();
        for (var key : keys) {
            var captured = new ArrayList<IPatternDetails>();
            var producers = catalog.get(key.toNative(1));
            if (producers != null) for (var pattern : producers) {
                var details = capture(pattern, substitutes, world);
                if (details != null) captured.add(details);
            }
            index.put(key, Collections.unmodifiableList(captured));
            if (crafting.canEmitFor(key.toNative(1))) emitted.add(key);
        }
        byOutput = Collections.unmodifiableMap(index);
        craftables = Collections.unmodifiableSet(outputs);
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
            if (REJECTED_TYPES.add(
                pattern.getClass()
                    .getName()))
                return null;
        }
        patterns.put(pattern, details);
        if (!pattern.isCraftable() && details.getDefinition() != null)
            processingByDefinition.computeIfAbsent(details.getDefinition(), ignored -> new ArrayList<>())
                .add(details);
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

    public List<ICraftingProvider> getProviders(IPatternDetails pattern) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        if (!(pattern instanceof NativePatternDetails requested) || !(crafting instanceof CraftingGridCache cache))
            throw new IllegalArgumentException("Unmapped source/native pattern provider identity");
        if (!isCatalogCurrent()) return Collections.emptyList();
        var direct = cache.getMediums(requested.nativeDetails());
        if (requested.nativeDetails()
            .isCraftable() || patterns.get(requested.nativeDetails()) == requested) {
            var result = new ArrayList<ICraftingProvider>();
            for (var medium : direct) result.add(provider(medium, requested, requested));
            return result;
        }

        var bindings = new IdentityHashMap<ICraftingMedium, NativePatternDetails>();
        var rebound = new ArrayList<ICraftingProvider>();
        for (var published : processingByDefinition.getOrDefault(requested.getDefinition(), Collections.emptyList())) {
            if (requested != published && !requested.matchesProcessingContract(published)) continue;
            for (var medium : cache.getMediums(published.nativeDetails())) {
                if (medium instanceof NativeSourceCraftingMedium && !direct.contains(medium)) continue;
                if (bindings.putIfAbsent(medium, published) == null)
                    rebound.add(provider(medium, requested, published));
            }
        }
        var result = new ArrayList<ICraftingProvider>();
        for (var medium : direct) {
            var published = bindings.get(medium);
            if (published != null) result.add(provider(medium, requested, published));
        }
        return result.isEmpty() ? rebound : result;
    }

    private ICraftingProvider provider(ICraftingMedium medium, NativePatternDetails requested,
        NativePatternDetails published) {
        return providers.computeIfAbsent(medium, ignored -> new IdentityHashMap<>())
            .computeIfAbsent(requested, ignored -> NativeCraftingProvider.wrap(medium, requested, published));
    }

    @Override
    public void addLink(CraftingLink link) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        if (!(crafting instanceof CraftingGridCache))
            throw new IllegalStateException("Pinned native crafting cache missing");
        ((CraftingGridCache) crafting).addLink(link.nativeLink());
    }

    @Override
    public long insertIntoCpus(AEKey key, long amount, appeng.api.config.Actionable mode) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        if (amount <= 0) return 0;
        if (!(crafting instanceof CraftingGridCache))
            throw new IllegalStateException("Pinned native crafting cache missing");
        var remainder = ((CraftingGridCache) crafting)
            .injectItems(key.toNative(amount), mode, new appeng.api.networking.security.BaseActionSource());
        long left = remainder == null ? 0 : remainder.getStackSize();
        if (left < 0 || left > amount) throw new IllegalStateException("Invalid native crafting output remainder");
        return amount - left;
    }

    /** The native cache owns the job index independently of this replaceable pattern snapshot. */
    @Override
    public long neoecoae$insertIntoCpuForJob(java.util.UUID jobId, AEKey key, long amount,
        appeng.api.config.Actionable mode) {
        return crafting instanceof ECOCraftingOutputRouter router
            ? router.neoecoae$insertIntoCpuForJob(jobId, key, amount, mode)
            : 0L;
    }

    @Override
    public java.math.BigInteger neoecoae$insertExactIntoCpuForJob(java.util.UUID jobId, AEKey key,
        java.math.BigInteger amount, appeng.api.config.Actionable mode) {
        return crafting instanceof ECOCraftingOutputRouter router
            ? router.neoecoae$insertExactIntoCpuForJob(jobId, key, amount, mode)
            : java.math.BigInteger.ZERO;
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
}
