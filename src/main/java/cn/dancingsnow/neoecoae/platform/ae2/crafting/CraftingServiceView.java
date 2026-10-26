package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collection;
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
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Frozen pattern contracts captured on the owning server thread; revision reads stay lock-free for the planner. */
public final class CraftingServiceView implements ICraftingService, ECOCraftingProviderRevision {

    private final ICraftingGrid crafting;
    private final NativePatternTrackingCache tracking;
    private final Map<ICraftingPatternDetails, NativePatternDetails> patterns = new IdentityHashMap<>();
    private final Map<AEKey, List<IPatternDetails>> byOutput;
    private final Set<AEKey> craftables;
    private final Set<AEKey> emitters;
    private final Map<appeng.api.networking.crafting.ICraftingMedium, Map<IPatternDetails, NativeCraftingProvider>> providers = new IdentityHashMap<>();

    public CraftingServiceView(IGrid grid, World world, Collection<AEKey> candidates,
        NativePatternTrackingCache tracking) {
        crafting = grid.getCache(ICraftingGrid.class);
        this.tracking = tracking;
        // The source compiler runs off-thread. Resolve every native pattern/recipe query here on the server thread.
        Set<AEKey> keys = new LinkedHashSet<>(candidates);
        Set<AEKey> outputs = new LinkedHashSet<>();
        for (var nativeKey : crafting.getCraftingMultiPatterns()
            .keySet()) {
            AEKey key = AEKey.fromNative(nativeKey);
            if (key == null) continue;
            outputs.add(key);
            keys.add(key);
            for (var pattern : crafting.getCraftingFor(nativeKey, null, 0, world)) {
                for (var input : pattern.getAEInputs()) if (input != null) keys.add(AEKey.fromNative(input));
                for (var output : pattern.getAEOutputs()) if (output != null) keys.add(AEKey.fromNative(output));
            }
        }
        keys.remove(null);
        Map<AEKey, List<IPatternDetails>> index = new java.util.LinkedHashMap<>();
        Set<AEKey> emitted = new LinkedHashSet<>();
        for (var key : keys) {
            var captured = new ArrayList<IPatternDetails>();
            for (var pattern : crafting.getCraftingFor(key.toNative(1), null, 0, world)) captured.add(
                patterns.computeIfAbsent(pattern, value -> NativePatternDetails.capture(value, world, candidates)));
            index.put(key, Collections.unmodifiableList(captured));
            if (crafting.canEmitFor(key.toNative(1))) emitted.add(key);
        }
        byOutput = Collections.unmodifiableMap(index);
        craftables = Collections.unmodifiableSet(outputs);
        emitters = Collections.unmodifiableSet(emitted);
    }

    @Override
    public List<IPatternDetails> getCraftingFor(AEKey key) {
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
        if (!(pattern instanceof NativePatternDetails) || !(crafting instanceof CraftingGridCache))
            throw new IllegalArgumentException("Unmapped source/native pattern provider identity");
        var result = new ArrayList<ICraftingProvider>();
        for (var medium : ((CraftingGridCache) crafting).getMediums(((NativePatternDetails) pattern).nativeDetails()))
            result.add(
                providers.computeIfAbsent(medium, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(
                        pattern,
                        ignored -> new NativeCraftingProvider(medium, Collections.singletonList(pattern))));
        return result;
    }

    @Override
    public void addLink(CraftingLink link) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        if (!(crafting instanceof CraftingGridCache))
            throw new IllegalStateException("Pinned native crafting cache missing");
        ((CraftingGridCache) crafting).addLink(link.nativeLink());
    }

    @Override
    public long neoecoae$getProviderRevision() {
        return tracking.revision();
    }

    @Override
    public boolean neoecoae$isProviderSnapshotStable() {
        return tracking.stable();
    }
}
