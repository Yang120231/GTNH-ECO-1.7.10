package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.world.WorldServer;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

/** An immutable catalog, deliberately containing no live grid reads in any planner-facing method. */
public final class SnapshotCraftingService implements ICraftingService, ECOCraftingProviderRevision {

    private final Map<AEKey, List<IPatternDetails>> recipes;
    private final Set<AEKey> emitted;
    private final long revision;
    private volatile boolean current = true;

    private SnapshotCraftingService(Map<AEKey, List<IPatternDetails>> recipes, Set<AEKey> emitted, long revision) {
        this.recipes = JdkCompat.mapCopyOf(recipes);
        this.emitted = JdkCompat.setCopyOf(emitted);
        this.revision = revision;
    }

    public static SnapshotCraftingService capture(ICraftingGrid grid, WorldServer world, KeyCounter stored,
        AEKey requestedGoal, long revision) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        Map<IAEStack<?>, ? extends List<ICraftingPatternDetails>> nativeRecipes = grid.getCraftingMultiPatterns();
        Set<AEKey> candidates = new LinkedHashSet<>(stored.keySet());
        candidates.add(Objects.requireNonNull(requestedGoal));
        for (IAEStack<?> output : nativeRecipes.keySet()) {
            AEKey key = AEKey.fromNative(output);
            if (key != null) candidates.add(key);
        }
        for (List<ICraftingPatternDetails> patterns : nativeRecipes.values())
            for (ICraftingPatternDetails pattern : patterns) for (IAEStack<?> input : pattern.getCondensedAEInputs()) {
                AEKey key = AEKey.fromNative(input);
                if (key != null) candidates.add(key);
            }
        Map<ICraftingPatternDetails, IPatternDetails> captured = new IdentityHashMap<>();
        Map<AEKey, List<IPatternDetails>> recipes = new LinkedHashMap<>();
        nativeRecipes.forEach((output, patterns) -> {
            AEKey key = AEKey.fromNative(output);
            if (key == null) throw new IllegalArgumentException("Unsupported native catalog output");
            List<IPatternDetails> values = new ArrayList<>();
            for (ICraftingPatternDetails pattern : patterns) values.add(
                captured.computeIfAbsent(pattern, value -> NativePatternDetails.capture(value, world, candidates)));
            recipes.put(key, JdkCompat.listCopyOf(values));
        });
        Set<AEKey> emitted = new HashSet<>();
        for (AEKey key : candidates) if (grid.canEmitFor(key.toNative(1))) emitted.add(key);
        return new SnapshotCraftingService(recipes, emitted, revision);
    }

    @Override
    public List<IPatternDetails> getCraftingFor(AEKey key) {
        return recipes.getOrDefault(key, Collections.emptyList());
    }

    @Override
    public Set<AEKey> getCraftables(Predicate<AEKey> filter) {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (AEKey key : recipes.keySet()) if (filter.test(key)) keys.add(key);
        return Collections.unmodifiableSet(keys);
    }

    @Override
    public boolean canEmitFor(AEKey key) {
        return emitted.contains(key);
    }

    @Override
    public long neoecoae$getProviderRevision() {
        return revision;
    }

    @Override
    public boolean neoecoae$isProviderSnapshotStable() {
        return current;
    }

    public void invalidate() {
        current = false;
    }
}
