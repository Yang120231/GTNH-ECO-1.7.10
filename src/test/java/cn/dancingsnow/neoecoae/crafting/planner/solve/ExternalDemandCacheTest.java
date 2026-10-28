package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ExternalDemandCacheTest {

    private final AEKey output = key("boundary"), raw = key("raw"), member = key("cycle");
    private final CompiledPattern recipe = pattern(output, raw, 2L);
    private final CompiledNetwork network = network(recipe);
    private final CycleComponent cycle = cycle(0, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
    private final AcyclicCraftingSolver solver = spy(new AcyclicCraftingSolver());
    private final ExternalDemandPlanner planner = new ExternalDemandPlanner(solver);

    @Test
    void identicalShortageAvoidsRepeatedExternalSolvesAcrossRenumberedComponents() throws Exception {
        var stock = stock(1L);
        var base = new SolveState(stock);
        var first = solve(network, cycle, 2L, stock, base);
        var renumbered = new CycleComponent(
            17,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(member),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                new ComponentDependency(
                    17,
                    23,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                        .listOf(new CraftingGraphEdge(member, output, null, null)))));
        var second = solve(network, renumbered, 2L, stock, base);

        assertEquals(CycleExternalDemandStatus.MISSING, second.status());
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 3L), first.missingLeaves());
        assertEquals(first.missingLeaves(), second.missingLeaves());
        assertNotSame(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(first.states()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(second.states()));
        assertExternalSolves(1);

        new ExternalDemandPlanner(solver).solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 2L),
            stock,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertExternalSolves(2); // A later invocation owns fresh proofs.
    }

    @Test
    void cachedSuccessfulStatesAndReservationsAreIsolatedFromTheirConsumers() throws Exception {
        var stock = stock(10L);
        stock.add(member, 2L);
        var base = new SolveState(stock);
        Map<AEKey, Long> demands = new LinkedHashMap<>();
        demands.put(member, 1L);
        demands.put(output, 2L);
        var first = planner.solveDemands(
            network,
            cycle,
            demands,
            stock,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertTrue(first.solved());
        var state = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(first.states());
        var originalProvenance = state.provenance.copy()
            .freeze();
        state.used.add(raw, 100L);
        state.patternTimes.clear();
        state.provenance.replaceWith(new cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialProvenance());
        first.directReservations()
            .set(member, 100L);
        first.selectedPatterns()
            .clear();

        var second = planner.solveDemands(
            network,
            cycle,
            demands,
            stock,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertEquals(
            1L,
            second.directReservations()
                .get(member));
        assertEquals(
            4L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(second.states())
                .usedItems()
                .get(raw));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(recipe.details(), 2L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(second.states())
                .patternTimes());
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(recipe.details()), second.selectedPatterns());
        assertEquals(
            originalProvenance,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(second.states())
                .executionProvenance());
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(second.states())
            .executionProvenance()
            .requireComplete();
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(second.states()).used.add(raw, 100L);
        second.directReservations()
            .set(member, 100L);

        var third = planner.solveDemands(
            network,
            cycle,
            demands,
            stock,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertEquals(
            4L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(third.states())
                .usedItems()
                .get(raw));
        assertEquals(
            1L,
            third.directReservations()
                .get(member));
        assertExternalSolves(1);
        assertEquals(10L, stock.get(raw));
        assertTrue(
            base.usedItems()
                .isEmpty());
    }

    @Test
    void cachedMissingStatesAndLeafCountsAreIsolated() throws Exception {
        var stock = stock(1L);
        var base = new SolveState(stock);
        var first = solve(network, cycle, 2L, stock, base);
        first.missingLeaves()
            .clear();
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(first.states()).missing.set(raw, PlannerAmount.ZERO);
        var second = solve(network, cycle, 2L, stock, base);

        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 3L), second.missingLeaves());
        assertEquals(
            3L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(second.states())
                .missingItems()
                .get(raw));
        assertExternalSolves(1);
    }

    @Test
    void anUnrepresentableLargeDemandIsSolvedOnceAndKeepsItsExactDiagnostic() throws Exception {
        var stock = new KeyCounter();
        var base = new SolveState(stock);
        var first = solve(network, cycle, Long.MAX_VALUE, stock, base);
        var second = solve(network, cycle, Long.MAX_VALUE, stock, base);

        assertEquals(CycleExternalDemandStatus.UNREPRESENTABLE, second.status());
        assertEquals(first.diagnostic(), second.diagnostic());
        assertTrue(
            second.diagnostic()
                .contains("18446744073709551614"),
            second.diagnostic());
        assertExternalSolves(1);
    }

    @Test
    void changedDemandRemainingInventoryOrReservationsRequiresANewSolve() throws Exception {
        var stock = stock(10L);
        var base = new SolveState(stock);
        assertTrue(solve(network, cycle, 2L, stock, base).solved());
        assertEquals(
            6L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(solve(network, cycle, 3L, stock, base).states())
                .usedItems()
                .get(raw));
        stock.set(raw, 1L);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 3L),
            solve(network, cycle, 2L, stock, base).missingLeaves());
        stock.set(raw, 10L);
        base.used.set(raw, PlannerAmount.of(9L));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 3L),
            solve(network, cycle, 2L, stock, base).missingLeaves());
        assertExternalSolves(3); // Equivalent remaining inventory can share a proof.

        base.used.set(raw, PlannerAmount.ZERO);
        var reserved = planner.solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 2L),
            stock,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 9L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 3L), reserved.missingLeaves());
        assertExternalSolves(4);
    }

    @Test
    void changedNetworkMembersOrExcludedRecipesRequiresANewSolve() throws Exception {
        var stock = stock(4L);
        var base = new SolveState(stock);
        assertTrue(solve(network, cycle, 2L, stock, base).solved());
        var excluded = solve(
            network,
            cycle(0, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(recipe)),
            2L,
            stock,
            base);
        assertEquals(CycleExternalDemandStatus.MISSING, excluded.status());
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 2L), excluded.missingLeaves());

        var differentMembers = new CycleComponent(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(recipe),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        assertEquals(
            CycleExternalDemandStatus.FORBIDDEN_ROUTE,
            solve(network, differentMembers, 2L, stock, base).status());

        var changedRecipe = pattern(output, raw, 3L);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 2L),
            solve(network(changedRecipe), cycle, 2L, stock, base).missingLeaves());
        assertExternalSolves(4);
    }

    @Test
    void unboundedSupplyDelegationAndSubstitutionSettingsRemainDistinct() throws Exception {
        var stock = new KeyCounter();
        stock.add(output, Long.MAX_VALUE);
        var finite = new SolveState(stock);
        var creative = new SolveState(
            PlannerInventorySnapshot.of(stock, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(output)));
        var delegated = planner.solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 1L),
            stock,
            finite,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(output),
            false,
            ECOCancellation.NONE);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 1L),
            delegated.delegatedCycleDemands());

        var creativeResult = planner.solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 1L),
            stock,
            creative,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(output),
            false,
            ECOCancellation.NONE);
        assertTrue(
            creativeResult.delegatedCycleDemands()
                .isEmpty());
        assertEquals(
            1L,
            creativeResult.directReservations()
                .get(output));
        var nonDelegated = planner.solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 1L),
            stock,
            finite,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            false,
            ECOCancellation.NONE);
        assertTrue(
            nonDelegated.delegatedCycleDemands()
                .isEmpty());
        assertEquals(
            1L,
            nonDelegated.directReservations()
                .get(output));

        var rawStock = stock(10L);
        var base = new SolveState(rawStock);
        solve(network, cycle, 2L, rawStock, base);
        planner.solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 2L),
            rawStock,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            true,
            ECOCancellation.NONE);
        assertExternalSolves(2);
    }

    @Test
    void cacheHitsHonorCancellationAndUnknownSearchResultsAreRetried() throws Exception {
        var stock = stock(10L);
        var base = new SolveState(stock);
        solve(network, cycle, 2L, stock, base);
        assertThrows(
            InterruptedException.class,
            () -> planner.solveDemands(
                network,
                cycle,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 2L),
                stock,
                base,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
                () -> { throw new InterruptedException("cancelled"); }));
        assertExternalSolves(1);

        var unknownSolver = mock(AcyclicCraftingSolver.class);
        var unknown = new AcyclicCraftingSolver.Outcome(
            PlanningStatus.CYCLE_UNRESOLVED,
            new SolveState(stock),
            new ECOPlanTrace());
        when(unknownSolver.solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any()))
            .thenReturn(unknown);
        var unknownPlanner = new ExternalDemandPlanner(unknownSolver);
        for (int i = 0; i < 2; i++) assertEquals(
            CycleExternalDemandStatus.UNSUPPORTED,
            unknownPlanner
                .solveDemands(
                    network,
                    cycle,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 2L),
                    stock,
                    base,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
                    ECOCancellation.NONE)
                .status());
        verify(unknownSolver, times(2)).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
    }

    @Test
    void anInterruptedSolveDoesNotLeaveACachedFailure() throws Exception {
        var stock = stock(10L);
        var base = new SolveState(stock);
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 1L, Long.MAX_VALUE, () -> 0L);
        assertThrows(
            ECOPlanningBudget.Exhausted.class,
            () -> planner.solveDemands(
                network,
                cycle,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, 2L),
                stock,
                base,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
                budget));
        assertTrue(solve(network, cycle, 2L, stock, base).solved());
        assertExternalSolves(1);
    }

    @Test
    void changedQuantitiesAndStockReuseOnlyTheFilteredStructure() throws Exception {
        var stock = stock(10L);
        var base = new SolveState(stock);
        assertEquals(
            4L,
            solve(network, cycle, 2L, stock, base).states()
                .get(0)
                .usedItems()
                .get(raw));
        assertEquals(
            6L,
            solve(network, cycle, 3L, stock, base).states()
                .get(0)
                .usedItems()
                .get(raw));
        stock.set(raw, 1L);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 3L),
            solve(network, cycle, 2L, stock, base).missingLeaves());
        var structures = org.mockito.ArgumentCaptor.forClass(CompiledNetwork.class);
        verify(solver, times(3))
            .solveDemands(structures.capture(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
        var actual = structures.getAllValues();
        assertSame(actual.get(0), actual.get(1));
        assertSame(actual.get(1), actual.get(2));

        var changed = network(pattern(output, raw, 3L));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 5L),
            solve(changed, cycle, 2L, stock, base).missingLeaves());
        var afterChange = org.mockito.ArgumentCaptor.forClass(CompiledNetwork.class);
        verify(solver, times(4))
            .solveDemands(afterChange.capture(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
        assertNotSame(
            actual.get(0),
            afterChange.getAllValues()
                .get(3));
    }

    private ExternalDemandPlanner.Outcome solve(CompiledNetwork graph, CycleComponent component, long amount,
        KeyCounter stock, SolveState base) throws Exception {
        return planner.solveDemands(
            graph,
            component,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(output, amount),
            stock,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
    }

    private void assertExternalSolves(int count) throws Exception {
        verify(solver, times(count)).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
    }

    private KeyCounter stock(long amount) {
        var result = new KeyCounter();
        result.add(raw, amount);
        return result;
    }

    private CompiledNetwork network(CompiledPattern producer) {
        return new CompiledNetwork(
            output,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                output,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(producer),
                raw,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            1);
    }

    private CycleComponent cycle(int id, List<CompiledPattern> excluded) {
        return new CycleComponent(
            id,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(member),
            excluded,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(AEKey output, AEKey input, long amount) {
        var details = mock(IPatternDetails.class);
        var outputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(output, 1L));
        when(details.getOutputs()).thenReturn(outputs);
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
            0,
            details,
            output,
            PlannerAmount.ONE,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new CompiledInput(null, input, amount, true, null)),
            outputs,
            true,
            null,
            false,
            semantics);
    }
}
