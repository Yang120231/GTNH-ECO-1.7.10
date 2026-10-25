package cn.dancingsnow.neoecoae.crafting.planner.solve;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Advances the nearest producer that can explain a failed leaf, without global route enumeration. */
public final class CandidateResolver {

    public boolean advanceAfterFailure(CompiledNetwork network, SolveState failed, Map<AEKey, Integer> choices) {
        ArrayDeque<AEKey> queue = new ArrayDeque<>();
        Set<AEKey> seen = new HashSet<>();
        for (var missing : failed.missing) {
            queue.add(missing.getKey());
            seen.add(missing.getKey());
        }
        for (AEKey unsupported : failed.unsupported) {
            if (seen.add(unsupported)) queue.add(unsupported);
        }
        while (!queue.isEmpty()) {
            AEKey failedKey = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.removeFirst(queue);
            for (AEKey parent : failed.parents
                .getOrDefault(failedKey, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf())) {
                List<CompiledPattern> candidates = network.fastProducersOf(parent);
                int current = choices.getOrDefault(parent, 0);
                if (current + 1 < candidates.size()) {
                    choices.put(parent, current + 1);
                    return true;
                }
                if (seen.add(parent)) queue.addLast(parent);
            }
        }
        return false;
    }
}
