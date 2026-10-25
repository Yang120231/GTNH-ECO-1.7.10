package cn.dancingsnow.neoecoae.crafting.planner.compile;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Bounded cross-order structural reuse. Inventory, numeric choices and search budgets are never cached. */
public final class CompiledStructureCache {

    private static final int MAX_ENTRIES = 32;
    private static final int MAX_WEIGHT = 50_000;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    @com.github.bsideup.jabel.Desugar
    public record Structure(CompiledNetwork network, CondensationGraph condensation) {}

    @com.github.bsideup.jabel.Desugar
    private record Entry(WeakReference<ICraftingService> owner, long revision, long contractGeneration, AEKey goal,
        boolean cycles, Set<ResourceLocation> fuzzyItems, Structure structure, int weight) {}

    private CompiledStructureCache() {}

    public static long revision(ICraftingService service) {
        return service instanceof ECOCraftingProviderRevision source && source.neoecoae$isProviderSnapshotStable()
            ? source.neoecoae$getProviderRevision()
            : -1;
    }

    public static synchronized Structure get(ICraftingService service, long revision, long contractGeneration,
        AEKey goal, boolean cycles, Set<ResourceLocation> fuzzyItems) {
        ENTRIES.removeIf(
            entry -> entry.owner()
                .get() == null || entry.contractGeneration() != contractGeneration
                || entry.owner()
                    .get() == service && entry.revision() != revision);
        if (revision < 0 || revision(service) != revision || PatternRuntime.reloadGeneration() != contractGeneration)
            return null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry entry = ENTRIES.get(i);
            if (matches(entry, service, revision, goal, cycles, fuzzyItems)) {
                ENTRIES.remove(i);
                ENTRIES.add(entry);
                return entry.structure();
            }
        }
        return null;
    }

    public static synchronized void put(ICraftingService service, long revision, long contractGeneration, AEKey goal,
        boolean cycles, Set<ResourceLocation> fuzzyItems, Structure structure) {
        if (revision < 0 || revision(service) != revision || PatternRuntime.reloadGeneration() != contractGeneration)
            return;
        long weight = (long) structure.network()
            .reachablePatternCount() + structure.network()
                .edgeCount()
            + structure.network()
                .keys()
                .size();
        if (weight > MAX_WEIGHT) return;
        ENTRIES.removeIf(
            entry -> entry.owner()
                .get() == null || entry.contractGeneration() != contractGeneration
                || entry.owner()
                    .get() == service
                    && (entry.revision() != revision || matches(entry, service, revision, goal, cycles, fuzzyItems)));
        int totalWeight = ENTRIES.stream()
            .mapToInt(Entry::weight)
            .sum();
        while (!ENTRIES.isEmpty() && (ENTRIES.size() >= MAX_ENTRIES || totalWeight + weight > MAX_WEIGHT)) {
            totalWeight -= cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.removeFirst(ENTRIES)
                .weight();
        }
        ENTRIES.add(
            new Entry(
                new WeakReference<>(service),
                revision,
                contractGeneration,
                goal,
                cycles,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(fuzzyItems),
                structure,
                (int) weight));
    }

    private static boolean matches(Entry entry, ICraftingService service, long revision, AEKey goal, boolean cycles,
        Set<ResourceLocation> fuzzyItems) {
        return entry.owner()
            .get() == service && entry.revision() == revision
            && entry.goal()
                .equals(goal)
            && entry.cycles() == cycles
            && entry.fuzzyItems()
                .equals(fuzzyItems);
    }
}
