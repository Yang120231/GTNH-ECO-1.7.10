package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/**
 * Block = 9 ingots + a reusable mold; the mold is cut from a block. Ores are imported without limit, so without a
 * mold the reachable markings are infinite and only a structural proof can answer before the budget runs out.
 */
class EmptySiphonDeadlockTest {

    private static final long WORK = 2_000_000L;
    private int ids;
    private final AEKey mold = key("mold");
    private final List<AEKey> members = new ArrayList<>();
    private final List<CompiledPattern> patterns = new ArrayList<>();
    private final List<ComponentDependency> boundary = new ArrayList<>();
    private AEKey block, ingot;

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 20 })
    void missingMoldIsProvenWithoutExhaustingTheBudget(int materials) throws Exception {
        build(materials);
        var work = new AtomicLong();
        var result = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(block, 1000L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            work);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
        assertTrue(
            result.diagnostics()
                .stream()
                .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.EMPTY_SIPHON_DEADLOCK),
            result.summary());
        assertEquals(
            1L,
            result.seedShortfall()
                .get(mold),
            result.summary());
        assertTrue(work.get() < WORK / 10, "Proof must not search the infinite marking space: " + work);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5 })
    void storedMoldStillSolves(int materials) throws Exception {
        build(materials);
        var result = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(block, 1000L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(mold, 1L),
            new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5 })
    void deadMoldPatternsDoNotBlockAReachableTarget(int materials) throws Exception {
        build(materials);
        var result = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(ingot, 1000L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertTrue(
            result.diagnostics()
                .stream()
                .noneMatch(d -> d.code() == CycleSolveDiagnostic.Code.EMPTY_SIPHON_DEADLOCK));
    }

    private void build(int materials) {
        members.add(mold);
        AEKey[] dust = new AEKey[materials];
        for (int i = 0; i < materials; i++) {
            dust[i] = key("dust" + i);
            AEKey ingotKey = key("ingot" + i), blockKey = key("block" + i), ore = key("ore" + i);
            members.addAll(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(dust[i], ingotKey, blockKey));
            var crush = pattern(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(ore, 1L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(dust[i], 2L));
            patterns.add(crush);
            boundary.add(
                new ComponentDependency(
                    0,
                    1,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                        .listOf(new CraftingGraphEdge(dust[i], ore, crush, null))));
            patterns.add(
                pattern(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(dust[i], 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(ingotKey, 1L)));
            patterns.add(
                pattern(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(ingotKey, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(dust[i], 1L)));
            patterns.add(
                pattern(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(ingotKey, 9L, mold, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(blockKey, 1L, mold, 1L)));
            patterns.add(
                pattern(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(blockKey, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(ingotKey, 9L)));
            if (i == 0) patterns.add(
                pattern(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(blockKey, 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(mold, 1L)));
            block = blockKey;
            ingot = ingotKey;
        }
        for (int i = 0; i + 1 < materials; i++) {
            AEKey alloy = key("alloy" + i);
            members.add(alloy);
            patterns.add(
                pattern(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(dust[i], 1L, dust[i + 1], 1L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(alloy, 2L)));
            patterns.add(
                pattern(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(alloy, 2L),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(dust[i], 1L, dust[i + 1], 1L)));
        }
    }

    private CycleSolveResult solve(Map<AEKey, Long> targets, Map<AEKey, Long> stock, AtomicLong work) throws Exception {
        var cycle = new CycleComponent(
            0,
            members,
            patterns,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            boundary);
        var request = new CycleSolveRequest(cycle, targets, stock, boundary, new CycleSolveRequest.PlannerOptions());
        return new BoundedCycleSolver()
            .solve(request, new ECOPlanningBudget(work::incrementAndGet, WORK, Long.MAX_VALUE, () -> 0L));
    }

    private static AEKey key(String name) {
        return mock(AEKey.class, name);
    }

    private CompiledPattern pattern(Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        int id = ids++;
        var details = mock(IPatternDetails.class, "pattern-" + id);
        var stacks = outputs.entrySet()
            .stream()
            .map(e -> new GenericStack(e.getKey(), e.getValue()))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        when(details.getOutputs()).thenReturn(stacks);
        var semantics = new PatternSemantics(
            details,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
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
            stacks.get(0)
                .what(),
            PlannerAmount.of(
                stacks.get(0)
                    .amount()),
            inputs.entrySet()
                .stream()
                .map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            stacks,
            true,
            null,
            false,
            semantics);
    }
}
