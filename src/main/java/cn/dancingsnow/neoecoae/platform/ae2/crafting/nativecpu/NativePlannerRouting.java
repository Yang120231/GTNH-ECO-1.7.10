package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/**
 * Decides whether a request must be planned by AE2's own planner because a reachable pattern uses semantics the ECO
 * planner does not model. Such a request is then also executed natively ({@code ECOVanillaCraftingExecutor}), so a
 * player never gets an ECO plan the GTNH CPU would have handled differently.
 */
final class NativePlannerRouting {

    /** Walk bound; an unusually large reachable graph keeps the ECO planner rather than stall the server thread. */
    private static final int MAX_VISITED_PATTERNS = 200_000;

    private NativePlannerRouting() {}

    /** @return the reason AE2's planner is required, or null if the ECO planner models every reachable pattern. */
    static String nativeOnlyReason(IGrid grid, IAEStack<?> output) {
        var cache = grid.getCache(ICraftingGrid.class);
        if (!(cache instanceof CraftingGridCache)) return null;
        var catalog = ((CraftingGridCache) cache).getCraftingMultiPatterns();
        return nativeOnlyReason(catalog::get, output, (CraftingGridCache) cache);
    }

    /** @param producersOf the native catalog lookup: patterns producing a stack, or null */
    static String nativeOnlyReason(Function<IAEStack<?>, ? extends Collection<ICraftingPatternDetails>> producersOf,
        IAEStack<?> output) {
        return nativeOnlyReason(producersOf, output, null);
    }

    private static String nativeOnlyReason(
        Function<IAEStack<?>, ? extends Collection<ICraftingPatternDetails>> producersOf, IAEStack<?> output,
        CraftingGridCache cache) {
        Set<AEKey> visitedKeys = new HashSet<>();
        Set<ICraftingPatternDetails> visitedPatterns = Collections.newSetFromMap(new IdentityHashMap<>());
        var queue = new ArrayDeque<IAEStack<?>>();
        queue.add(output);
        while (!queue.isEmpty()) {
            IAEStack<?> stack = queue.poll();
            AEKey key = AEKey.fromNative(stack);
            if (key == null || !visitedKeys.add(key)) continue;
            var producers = producersOf.apply(stack);
            if (producers == null) continue;
            for (var pattern : producers) {
                if (!visitedPatterns.add(pattern)) continue;
                if (visitedPatterns.size() > MAX_VISITED_PATTERNS) return null;
                String reason = unsupported(pattern);
                if (reason != null) return reason;
                var inputs = NativePatternDetails.expandInputs(pattern, cache, true);
                if (inputs == null) return "invalid tunnel pattern expansion";
                for (var input : inputs) if (input != null) queue.add(input);
            }
        }
        return null;
    }

    static String unsupported(ICraftingPatternDetails pattern) {
        if (pattern.isInputOnly()) return "input-only pattern";
        // GTNH substitutes processing inputs with FuzzyMode.IGNORE_ALL; ECO only substitutes crafting recipes.
        if (!pattern.isCraftable() && pattern.canSubstitute()) return "processing pattern with substitution";
        return null;
    }

    static boolean routeToNative(IGrid grid, IAEStack<?> output) {
        String reason;
        try {
            reason = nativeOnlyReason(grid, output);
        } catch (RuntimeException failure) {
            return true;
        }
        return reason != null;
    }
}
