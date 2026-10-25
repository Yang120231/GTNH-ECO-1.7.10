package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class JointOrderedProposalTest {

    @Test
    void selfReturnedPrimaryCannotActivateExtraUnadvertisedCoproductFirings() throws Exception {
        AEKey seed = key("catalyst"), raw = key("raw"), side = key("side"), goal = key("goal");
        var producer = pattern(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(seed, 1), s(raw, 1)),
            s(seed, 1),
            s(side, 1));
        var finish = pattern(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(seed, 1), s(side, 10)),
            s(goal, 1));
        var stock = new KeyCounter();
        stock.add(seed, 1);
        stock.add(raw, 10);
        assertNull(
            new JointRouteOptimizer().optimize(
                index(goal, false, producer, finish),
                PlannerInventorySnapshot.of(stock),
                1,
                null,
                true,
                ECOCancellation.NONE));
        var advertised = new JointRouteOptimizer().optimize(
            index(goal, true, producer, finish),
            PlannerInventorySnapshot.of(stock),
            1,
            null,
            true,
            ECOCancellation.NONE);
        assertNotNull(advertised);
        assertEquals(PlanningStatus.SUCCESS, advertised.status());
        assertEquals(
            10L,
            advertised.state()
                .patternTimes()
                .get(producer.details()));
    }

    @Test
    void seedFreeLoopCannotBecomeAPlanAndLargeSeededLoopHasACompactWitness() throws Exception {
        AEKey seed = key("seed"), goal = key("goal");
        var grow = pattern(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(seed, 1)),
            s(seed, 1),
            s(goal, 1));
        var network = index(goal, true, grow);
        assertNull(
            new JointRouteOptimizer()
                .optimize(network, PlannerInventorySnapshot.of(new KeyCounter()), 1, null, true, ECOCancellation.NONE));
        var stock = new KeyCounter();
        stock.add(seed, 1);
        var result = new JointRouteOptimizer().optimize(
            network,
            PlannerInventorySnapshot.of(stock),
            1_000_000_000_000L,
            null,
            true,
            ECOCancellation.NONE);
        assertNotNull(result);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(
            1_000_000_000_000L,
            result.state()
                .patternTimes()
                .get(grow.details()));
        assertTrue(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(result.components())
                .cycleResult()
                .executionPlan()
                .size() < 10);
    }

    @Test
    void dominanceCannotReplaceAnAdvertisedViewWithAnUnadvertisedCoproduct() throws Exception {
        AEKey raw = key("raw"), goal = key("goal"), unused = key("unused");
        var unrelated = pattern(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(raw, 1)),
            s(unused, 1),
            s(goal, 1));
        var advertised = pattern(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(raw, 1)),
            s(goal, 1),
            s(unused, 1));
        var network = index(goal, false, unrelated, advertised);
        var stock = new KeyCounter();
        stock.add(raw, 1);
        var result = new JointRouteOptimizer()
            .optimize(network, PlannerInventorySnapshot.of(stock), 1, null, true, ECOCancellation.NONE);
        assertNotNull(result);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(advertised.details(), 1L),
            result.state()
                .patternTimes());
    }

    @Test
    void candidateCountBeforeSafeDeduplicationDoesNotDisableTheSolver() throws Exception {
        AEKey raw = key("raw"), goal = key("goal");
        var patterns = new CompiledPattern[140];
        for (int i = 0; i < patterns.length; i++)
            patterns[i] = pattern(i, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(raw, 1)), s(goal, 1));
        var stock = new KeyCounter();
        stock.add(raw, 1);
        var result = new JointRouteOptimizer().optimize(
            index(goal, true, patterns),
            PlannerInventorySnapshot.of(stock),
            1,
            null,
            true,
            ECOCancellation.NONE);
        assertNotNull(result);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(
            1,
            result.state()
                .patternTimes()
                .size());
    }

    private static CompiledNetwork index(AEKey goal, boolean allOutputs, CompiledPattern... patterns) {
        Map<AEKey, List<CompiledPattern>> index = new LinkedHashMap<>();
        for (var pattern : patterns) {
            for (var input : pattern.inputs()) index.putIfAbsent(input.key(), new ArrayList<>());
            var advertised = allOutputs ? pattern.outputs()
                : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(pattern.outputs()));
            for (var output : advertised) index.computeIfAbsent(output.what(), ignored -> new ArrayList<>())
                .add(
                    new CompiledPattern(
                        pattern.id(),
                        pattern.details(),
                        output.what(),
                        PlannerAmount.of(output.amount()),
                        pattern.inputs(),
                        pattern.outputs(),
                        true,
                        null,
                        false,
                        pattern.semantics()));
        }
        return new CompiledNetwork(
            goal,
            index,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            patterns.length,
            patterns.length);
    }

    @Test
    void actualChromiumOrderManufacturesTheFullAcidPrefixFromOriginalRawStock() throws Exception {
        AEKey acid = key("acid"), chlorine = key("chlorine"), hydrogen = key("hydrogen"), water = key("water"),
            oxygen = key("oxygen"), ore = key("ore"), ruby = key("ruby"), aluminium = key("aluminium"),
            solution = key("solution"), chromium = key("chromium"), salt = key("salt"), sodium = key("sodium");
        var dissolve = pattern(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(acid, 9000), s(ore, 1)),
            s(solution, 9000));
        var makeAcid = pattern(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(chlorine, 1000), s(hydrogen, 1000)),
            s(acid, 2000));
        var waterSplit = pattern(
            2,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(water, 3000)),
            s(hydrogen, 2000),
            s(oxygen, 1000));
        var rubySplit = pattern(
            3,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(ruby, 6)),
            s(ore, 1),
            s(aluminium, 2));
        var recover = pattern(
            4,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(solution, 1000)),
            s(chromium, 3),
            s(chlorine, 450),
            s(hydrogen, 450));
        var saltSplit = pattern(
            5,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(s(salt, 2)),
            s(sodium, 1),
            s(chlorine, 125));
        Map<AEKey, List<CompiledPattern>> index = new LinkedHashMap<>();
        for (var pattern : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .listOf(dissolve, makeAcid, waterSplit, rubySplit, recover, saltSplit)) {
            for (var input : pattern.inputs()) index.putIfAbsent(input.key(), new ArrayList<>());
            for (var output : pattern.outputs()) index.computeIfAbsent(output.what(), ignored -> new ArrayList<>())
                .add(
                    new CompiledPattern(
                        pattern.id(),
                        pattern.details(),
                        output.what(),
                        PlannerAmount.of(output.amount()),
                        pattern.inputs(),
                        pattern.outputs(),
                        true,
                        null,
                        false,
                        pattern.semantics()));
        }
        var network = new CompiledNetwork(
            chromium,
            index,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            6,
            8);
        var stock = new KeyCounter();
        stock.add(water, 100000);
        stock.add(ruby, 100);
        stock.add(salt, 1000);
        var result = new JointRouteOptimizer()
            .optimize(network, PlannerInventorySnapshot.of(stock), 1, null, true, ECOCancellation.NONE);
        assertNotNull(result);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertTrue(
            result.state()
                .patternTimes()
                .get(makeAcid.details()) >= 5);
        assertTrue(
            result.state()
                .patternTimes()
                .get(saltSplit.details()) >= 40);
        assertFalse(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(result.components())
                .cycleResult()
                .executionPlan()
                .isEmpty());
        result.state()
            .executionProvenance()
            .requireComplete();
    }

    private static GenericStack s(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(int id, List<GenericStack> in, GenericStack... out) {
        var details = mock(IPatternDetails.class);
        var outputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(out);
        when(details.getOutputs()).thenReturn(outputs);
        var rawInputs = in.stream()
            .map(stack -> {
                var input = mock(IPatternDetails.IInput.class);
                when(input.getPossibleInputs()).thenReturn(new GenericStack[] { stack });
                when(input.getMultiplier()).thenReturn(1L);
                return input;
            })
            .toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(rawInputs);
        var semantics = new PatternSemantics(
            details,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            outputs,
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
            out[0].what(),
            PlannerAmount.of(out[0].amount()),
            in.stream()
                .map(input -> new CompiledInput(null, input.what(), input.amount(), true, null))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            outputs,
            true,
            null,
            false,
            semantics);
    }
}
