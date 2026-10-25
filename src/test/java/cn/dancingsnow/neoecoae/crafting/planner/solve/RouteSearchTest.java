package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class RouteSearchTest {

    private int nextId;

    @Test
    void unreachableChoicesDoNotMultiplyRoutes() throws Exception {
        var goal = key("goal");
        var a = key("a");
        var b = key("b");
        var x = key("x");
        var y = key("y");
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                pattern(
                    goal,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L)),
                pattern(
                    goal,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L))));
        producers.put(
            a,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                pattern(
                    a,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(x, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L)),
                pattern(
                    a,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(y, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L))));
        producers.put(
            b,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                pattern(
                    b,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(x, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L)),
                pattern(
                    b,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(y, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L))));
        producers.put(x, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        producers.put(y, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var network = network(goal, producers);
        var search = new RouteSearch(
            network,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 0, a, 0, b, 0));

        var expected = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1, a, 0, b, 0),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 0, a, 1, b, 0),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1, a, 0, b, 1));
        var passes = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(1, 1, 2);
        for (int i = 0; i < expected.size(); i++) {
            var route = search.next(ECOCancellation.NONE);
            assertNotNull(route, "route " + i);
            assertEquals(
                expected.get(i),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .mapOf(goal, route.get(goal), a, route.get(a), b, route.get(b)));
            assertEquals(passes.get(i), search.pass());
        }
        assertNull(search.next(ECOCancellation.NONE));
        assertTrue(search.expansions() > 0);
    }

    @Test
    void byproductProducerChoiceIsReachable() throws Exception {
        var goal = key("g");
        var k = key("k");
        var main = key("K");
        var z = key("z");
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(
            goal,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                pattern(
                    goal,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(k, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 1L))));
        producers.put(k, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var withByproduct = new LinkedHashMap<AEKey, Long>();
        withByproduct.put(main, 1L);
        withByproduct.put(k, 1L);
        producers.put(
            main,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                pattern(main, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(z, 1L), withByproduct),
                pattern(
                    main,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(z, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(main, 1L))));
        producers.put(z, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var network = network(goal, producers);
        var search = new RouteSearch(
            network,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(goal, 0, k, 0, main, 0, z, 0));

        var route = search.next(ECOCancellation.NONE);
        assertNotNull(route);
        assertEquals(1, route.get(main));
        assertEquals(0, route.get(goal));
        assertNull(search.next(ECOCancellation.NONE));
    }

    @Test
    void matchesBruteForceCanonicalRoutes() throws Exception {
        for (long seed : new long[] { 1, 2, 3, 7, 11, 42, 99, 2024, 31337, 123456 }) {
            verifyAgainstBruteForce(seed);
        }
    }

    private void verifyAgainstBruteForce(long seed) throws Exception {
        var random = new Random(seed);
        int keyCount = 6;
        var keys = new ArrayList<AEKey>();
        for (int i = 0; i < keyCount; i++) keys.add(key("k" + i));
        var goal = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(keys);
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        for (var key : keys) {
            var patterns = new ArrayList<CompiledPattern>();
            int candidates = random.nextInt(4);
            for (int i = 0; i < candidates; i++) {
                var inputs = new LinkedHashMap<AEKey, Long>();
                int inputCount = 1 + random.nextInt(2);
                for (int j = 0; j < inputCount; j++) inputs.put(keys.get(random.nextInt(keyCount)), 1L);
                patterns.add(pattern(key, inputs, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, 1L)));
            }
            producers.put(key, patterns);
        }
        var network = network(goal, producers);
        var variable = keys.stream()
            .filter(
                key -> producers.get(key)
                    .size() > 1)
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var baseline = new LinkedHashMap<AEKey, Integer>();
        for (var key : keys) baseline.put(
            key,
            producers.get(key)
                .isEmpty() ? 0
                    : random.nextInt(
                        producers.get(key)
                            .size()));
        var baselineCanonical = canonical(producers, goal, variable, baseline);

        // Brute force: every full assignment over the variable keys, reduced to its reached part.
        var expected = new HashSet<Map<AEKey, Integer>>();
        int total = 1;
        for (var key : variable) total *= producers.get(key)
            .size();
        for (int code = 0; code < total; code++) {
            var assignment = new LinkedHashMap<>(baseline);
            int rest = code;
            for (var key : variable) {
                int size = producers.get(key)
                    .size();
                assignment.put(key, rest % size);
                rest /= size;
            }
            expected.add(canonical(producers, goal, variable, assignment));
        }
        expected.remove(baselineCanonical);

        var search = new RouteSearch(network, baseline);
        var actual = new HashSet<Map<AEKey, Integer>>();
        int previousDeviations = 0;
        Map<AEKey, Integer> route;
        while ((route = search.next(ECOCancellation.NONE)) != null) {
            var canonical = canonical(producers, goal, variable, route);
            assertTrue(actual.add(canonical), "duplicate canonical route, seed " + seed);
            for (var key : keys) {
                assertEquals(
                    canonical.getOrDefault(key, baseline.get(key)),
                    route.get(key),
                    "unreached key must stay at baseline, seed " + seed);
            }
            int deviations = 0;
            for (var entry : canonical.entrySet()) {
                if (!entry.getValue()
                    .equals(baseline.get(entry.getKey()))) deviations++;
            }
            assertTrue(deviations >= previousDeviations, "deviations must not decrease, seed " + seed);
            assertEquals(deviations, search.pass(), "pass is the deviation count, seed " + seed);
            previousDeviations = deviations;
        }
        assertEquals(expected, actual, "seed " + seed);
    }

    /** Independent reachability over chosen inputs only; the canonical form is the reached variable choices. */
    private static Map<AEKey, Integer> canonical(Map<AEKey, List<CompiledPattern>> producers, AEKey goal,
        List<AEKey> variable, Map<AEKey, Integer> assignment) {
        var reached = new LinkedHashSet<AEKey>();
        var work = new ArrayDeque<AEKey>();
        reached.add(goal);
        work.add(goal);
        while (!work.isEmpty()) {
            var key = work.poll();
            var candidates = producers.get(key);
            if (candidates.isEmpty()) continue;
            int choice = Math.min(assignment.getOrDefault(key, 0), candidates.size() - 1);
            for (var input : candidates.get(choice)
                .inputs()) {
                if (reached.add(input.key())) work.add(input.key());
            }
        }
        var result = new HashMap<AEKey, Integer>();
        for (var key : variable) {
            if (reached.contains(key)) result.put(
                key,
                Math.min(
                    assignment.getOrDefault(key, 0),
                    producers.get(key)
                        .size() - 1));
        }
        return result;
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledNetwork network(AEKey goal, Map<AEKey, List<CompiledPattern>> producers) {
        return new CompiledNetwork(
            goal,
            producers,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            producers.values()
                .stream()
                .mapToInt(List::size)
                .sum(),
            10);
    }

    private CompiledPattern pattern(AEKey primary, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        int id = nextId++;
        var details = mock(IPatternDetails.class, "pattern" + id);
        var stacks = new ArrayList<GenericStack>();
        stacks.add(new GenericStack(primary, outputs.get(primary)));
        outputs.forEach((key, amount) -> { if (!key.equals(primary)) stacks.add(new GenericStack(key, amount)); });
        var rawInputs = inputs.entrySet()
            .stream()
            .map(entry -> {
                var input = mock(IPatternDetails.IInput.class);
                when(input.getPossibleInputs())
                    .thenReturn(new GenericStack[] { new GenericStack(entry.getKey(), entry.getValue()) });
                when(input.getMultiplier()).thenReturn(1L);
                return input;
            })
            .toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(rawInputs);
        when(details.getOutputs()).thenReturn(stacks);
        when(details.getPrimaryOutput()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(stacks));
        var compiled = inputs.entrySet()
            .stream()
            .map(entry -> new CompiledInput(null, entry.getKey(), entry.getValue(), true, null))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var consumed = inputs.entrySet()
            .stream()
            .map(
                entry -> new PatternSemantics.Input(
                    null,
                    entry.getKey(),
                    PlannerAmount.of(entry.getValue()),
                    null,
                    PlannerAmount.ZERO))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var semantics = new PatternSemantics(
            details,
            null,
            consumed,
            stacks,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        return new CompiledPattern(
            id,
            details,
            primary,
            PlannerAmount.of(outputs.get(primary)),
            compiled,
            stacks,
            true,
            null,
            false,
            semantics);
    }
}
