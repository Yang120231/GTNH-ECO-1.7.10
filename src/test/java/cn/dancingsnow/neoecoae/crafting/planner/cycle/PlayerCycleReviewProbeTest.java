package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class PlayerCycleReviewProbeTest {

    private final AEKey a = mock(AEKey.class, "A");
    private final AEKey b = mock(AEKey.class, "B");
    private final AEKey fuel = mock(AEKey.class, "fuel");
    private final AEKey product = mock(AEKey.class, "product");

    @Test
    void neutralTwoPatternRingUsesExactPlanForLargePlayerRequest() throws Exception {
        var produceIntermediate = pattern(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, fuel, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L));
        var finish = pattern(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, product, 1L));
        var result = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a, b),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(produceIntermediate, finish),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(product, 50_000L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L));
        org.junit.jupiter.api.Assertions.assertEquals(
            CycleSolveStatus.SUCCESS,
            result.status(),
            result.diagnostics()
                .toString());
        org.junit.jupiter.api.Assertions.assertEquals(
            50_000L,
            result.patternTimes()
                .get(produceIntermediate.details()));
        org.junit.jupiter.api.Assertions.assertEquals(
            50_000L,
            result.patternTimes()
                .get(finish.details()));
        org.junit.jupiter.api.Assertions.assertEquals(
            50_000L,
            result.externalDemand()
                .get(fuel));
    }

    private CycleSolveResult solve(List<AEKey> members, List<CompiledPattern> patterns, Map<AEKey, Long> targets,
        Map<AEKey, Long> stock) throws Exception {
        var boundary = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            new ComponentDependency(
                0,
                1,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                    new CraftingGraphEdge(
                        a,
                        fuel,
                        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(patterns),
                        null))));
        var cycle = new CycleComponent(
            0,
            members,
            patterns,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            boundary);
        return new BoundedCycleSolver().solve(
            new CycleSolveRequest(cycle, targets, stock, boundary, new CycleSolveRequest.PlannerOptions()),
            ECOCancellation.NONE);
    }

    private static CompiledPattern pattern(int id, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        var details = mock(IPatternDetails.class, "pattern" + id);
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
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(stacks)
                .what(),
            PlannerAmount.of(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(stacks)
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
