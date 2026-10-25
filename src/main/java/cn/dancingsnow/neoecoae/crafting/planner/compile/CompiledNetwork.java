package cn.dancingsnow.neoecoae.crafting.planner.compile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

@com.github.bsideup.jabel.Desugar
public record CompiledNetwork(AEKey goal, Map<AEKey, List<CompiledPattern>> producers, Set<AEKey> emittable,
    int reachablePatternCount, int edgeCount, Map<AEKey, List<CompiledPattern>> fastProducers, boolean multiplePaths) {

    public CompiledNetwork(AEKey goal, Map<AEKey, List<CompiledPattern>> producers, Set<AEKey> emittable,
        int reachablePatternCount, int edgeCount) {
        this(
            goal,
            producers,
            emittable,
            reachablePatternCount,
            edgeCount,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            false);
    }

    public CompiledNetwork {
        Map<AEKey, List<CompiledPattern>> all = new LinkedHashMap<>();
        Map<AEKey, List<CompiledPattern>> fast = new LinkedHashMap<>();
        multiplePaths = false;
        for (var entry : producers.entrySet()) {
            List<CompiledPattern> patterns = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listCopyOf(entry.getValue());
            all.put(entry.getKey(), patterns);
            fast.put(
                entry.getKey(),
                patterns.stream()
                    .filter(CompiledPattern::fastSupported)
                    .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()));
            multiplePaths |= patterns.size() > 1;
        }
        producers = java.util.Collections.unmodifiableMap(all);
        fastProducers = java.util.Collections.unmodifiableMap(fast);
        emittable = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(emittable);

    }

    public Set<AEKey> keys() {
        return producers.keySet();
    }

    public List<CompiledPattern> producersOf(AEKey key) {
        return producers.getOrDefault(key, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
    }

    public List<CompiledPattern> fastProducersOf(AEKey key) {
        return fastProducers.getOrDefault(key, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
    }
}
