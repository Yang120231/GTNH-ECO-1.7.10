package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingDependencyGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphNode;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class ActiveRouteSelectorTest {

    @Test
    void jumpsOverCandidatesThatCloseTheCurrentReachableCycle() throws Exception {
        AEKey a = mock(AEKey.class);
        AEKey b = mock(AEKey.class);
        List<CompiledPattern> aCandidates = new ArrayList<>();
        for (int i = 0; i < 14; i++) aCandidates.add(pattern(i, a, b));
        aCandidates.add(pattern(14, a, null));
        CompiledPattern bCandidate = pattern(15, b, a);

        Map<AEKey, CraftingGraphNode> nodes = new LinkedHashMap<>();
        nodes.put(a, new CraftingGraphNode(a, aCandidates));
        nodes.put(b, new CraftingGraphNode(b, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(bCandidate)));
        CraftingDependencyGraph graph = new CraftingDependencyGraph(
            a,
            nodes,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());

        ActiveRouteSelector.Selection selection = new ActiveRouteSelector().select(graph, ECOCancellation.NONE);

        assertTrue(selection.acyclic());
        assertEquals(ActiveRouteSelector.Selection.Status.ACYCLIC, selection.status());
        assertFalse(selection.budgetExhausted());
        assertEquals(
            14,
            selection.choices()
                .get(a));
        assertTrue(
            selection.condensation()
                .cycles()
                .isEmpty());
    }

    private static CompiledPattern pattern(int id, AEKey output, AEKey input) {
        IPatternDetails details = mock(IPatternDetails.class);
        GenericStack product = new GenericStack(output, 1L);
        when(details.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(details.getOutputs()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product));
        List<CompiledInput> inputs = input == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()
            : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new CompiledInput(null, input, 1L, true, null));
        List<PatternSemantics.Input> consumed = input == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()
            : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new PatternSemantics.Input(null, input, PlannerAmount.ONE, null, PlannerAmount.ZERO));
        PatternSemantics semantics = new PatternSemantics(
            details,
            null,
            consumed,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product),
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
            output,
            PlannerAmount.ONE,
            inputs,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(product),
            true,
            null,
            true,
            semantics);
    }
}
