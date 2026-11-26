package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Server-only execution lookups. No inventory listing and no access to the planner's full capture path. */
public final class NativeLiveCraftingService
    implements ICraftingService, ECOCraftingProviderRevision, ECOCraftingOutputRouter {

    private final World world;
    private final CraftingGridCache cache;
    private final NativePatternTrackingCache tracking;
    // Cursor state is owned by the job, not by this bounded memo of recently resolved native keys.
    private final Cache<NativePatternDetails, Binding> bindings = CacheBuilder.newBuilder()
        .maximumSize(2048)
        .build();

    private static final class Binding {

        final long revision;
        final List<ICraftingProvider> providers;

        Binding(long revision, List<ICraftingProvider> providers) {
            this.revision = revision;
            this.providers = providers;
        }
    }

    NativeLiveCraftingService(World world, CraftingGridCache cache, NativePatternTrackingCache tracking) {
        this.world = world;
        this.cache = cache;
        this.tracking = tracking;
    }

    @Override
    public long neoecoae$getProviderRevision() {
        return tracking.liveRevision();
    }

    @Override
    public List<ICraftingProvider> getProviders(IPatternDetails pattern) {
        return getProviders(pattern, false);
    }

    List<ICraftingProvider> getProviders(IPatternDetails pattern, boolean directOnly) {
        ServerTasks.assertServerThread();
        if (!(pattern instanceof NativePatternDetails requested))
            throw new IllegalArgumentException("Unmapped native provider identity");
        long revision = tracking.liveRevision();
        Binding old = bindings.getIfPresent(requested);
        if (!directOnly && old != null && old.revision == revision) return old.providers;
        Map<ICraftingMedium, NativeCraftingProvider> previous = new IdentityHashMap<>();
        if (old != null)
            for (var provider : old.providers) if (provider instanceof NativeCraftingProvider nativeProvider)
                previous.put(nativeProvider.nativeMedium(), nativeProvider);
        List<ICraftingProvider> found = resolve(requested, directOnly, previous);
        if (!directOnly && tracking.liveRevision() == revision) bindings.put(requested, new Binding(revision, found));
        return found;
    }

    private List<ICraftingProvider> resolve(NativePatternDetails requested, boolean directOnly,
        Map<ICraftingMedium, NativeCraftingProvider> previous) {
        var nativeKey = requested.nativeDetails();
        var direct = cache.getMediums(nativeKey);
        if (directOnly && direct.isEmpty()) return Collections.emptyList();
        List<ICraftingProvider> result = new ArrayList<>();
        if (nativeKey.isCraftable() || tracking.owns(world, requested)) {
            for (var medium : direct) result.add(bind(previous, medium, requested, requested));
            return Collections.unmodifiableList(result);
        }
        if (requested.getOutputs()
            .isEmpty()) return Collections.emptyList();
        var bucket = cache.getCraftingMultiPatterns()
            .get(
                requested.getPrimaryOutput()
                    .what()
                    .toNative(1));
        if (bucket == null) return Collections.emptyList();
        Map<ICraftingMedium, ICraftingProvider> matches = new IdentityHashMap<>();
        for (var candidate : bucket) {
            if (candidate.isCraftable() || directOnly && !nativeKey.equals(candidate)
                || !java.util.Objects.equals(requested.getDefinition(), AEItemKey.of(candidate.getPattern()))) continue;
            var published = detail(candidate);
            if (published == null || !requested.matchesProcessingContract(published)) continue;
            for (var medium : cache.getMediums(candidate)) {
                if (medium instanceof NativeSourceCraftingMedium && !direct.contains(medium)
                    || matches.containsKey(medium)) continue;
                var provider = bind(previous, medium, requested, published);
                matches.put(medium, provider);
                result.add(provider);
            }
        }
        List<ICraftingProvider> preferred = new ArrayList<>();
        for (var medium : direct) if (matches.containsKey(medium)) preferred.add(matches.get(medium));
        return Collections.unmodifiableList(!directOnly && preferred.isEmpty() ? result : preferred);
    }

    private static ICraftingProvider bind(Map<ICraftingMedium, NativeCraftingProvider> previous, ICraftingMedium medium,
        NativePatternDetails requested, NativePatternDetails published) {
        var old = previous.get(medium);
        return old != null && old.nativePatternFor(requested)
            .nativeDetails() == published.nativeDetails() ? old
                : NativeCraftingProvider.wrap(medium, requested, published);
    }

    private NativePatternDetails detail(ICraftingPatternDetails pattern) {
        try {
            return !pattern.isCraftable() && NativePatternDetails.hasTunnelInputs(pattern.getAEInputs())
                ? NativePatternDetails.capture(pattern, world, (p, i, k, s) -> Collections.emptyList(), cache)
                : NativePatternDetails.capture(pattern, world, Collections.emptyList());
        } catch (IllegalArgumentException rejected) {
            return null;
        }
    }

    @Override
    public boolean canEmitFor(AEKey key) {
        ServerTasks.assertServerThread();
        return cache.canEmitFor(key.toNative(1));
    }

    @Override
    public List<IPatternDetails> getCraftingFor(AEKey key) {
        ServerTasks.assertServerThread();
        var temporary = TemporaryCraftingOrder.decode(key);
        if (temporary != null) return Collections.singletonList(temporary);
        var bucket = cache.getCraftingMultiPatterns()
            .get(key.toNative(1));
        if (bucket == null) return Collections.emptyList();
        List<IPatternDetails> result = new ArrayList<>();
        for (var nativePattern : bucket) {
            var pattern = detail(nativePattern);
            if (pattern != null) result.add(pattern);
        }
        return result;
    }

    @Override
    public Set<AEKey> getCraftables(AEKeyFilter filter) {
        ServerTasks.assertServerThread();
        Set<AEKey> result = new LinkedHashSet<>();
        for (var nativeKey : cache.getCraftingMultiPatterns()
            .keySet()) {
            AEKey key = AEKey.fromNative(nativeKey);
            if (key != null && filter.matches(key)) result.add(key);
        }
        return result;
    }

    @Override
    public void addLink(CraftingLink link) {
        ServerTasks.assertServerThread();
        cache.addLink(link.nativeLink());
    }

    @Override
    public long insertIntoCpus(AEKey key, long amount, appeng.api.config.Actionable mode) {
        ServerTasks.assertServerThread();
        if (amount <= 0) return 0;
        var rest = cache.injectItems(key.toNative(amount), mode, new appeng.api.networking.security.BaseActionSource());
        long left = rest == null ? 0 : rest.getStackSize();
        if (left < 0 || left > amount) throw new IllegalStateException("Invalid native crafting output remainder");
        return amount - left;
    }

    @Override
    public long neoecoae$insertIntoCpuForJob(java.util.UUID job, AEKey key, long amount,
        appeng.api.config.Actionable mode) {
        return cache instanceof ECOCraftingOutputRouter router
            ? router.neoecoae$insertIntoCpuForJob(job, key, amount, mode)
            : 0;
    }

    @Override
    public java.math.BigInteger neoecoae$insertExactIntoCpuForJob(java.util.UUID job, AEKey key,
        java.math.BigInteger amount, appeng.api.config.Actionable mode) {
        return cache instanceof ECOCraftingOutputRouter router
            ? router.neoecoae$insertExactIntoCpuForJob(job, key, amount, mode)
            : java.math.BigInteger.ZERO;
    }
}
