package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class AERecipeCircuitTest {

    @BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap.initialize();
    }

    @Test
    void returnedCatalystsReserveOnlyTheStartupCopyAndLeaveOtherCopiesForConcurrentJobs() throws Exception {
        AEKey catalyst = AEItemKey.of(TestItems.DIAMOND), product = AEItemKey.of(TestItems.EMERALD);
        AEKey fuel = AEFluidKey.of(TestFluids.WATER);
        var recipe = recipe(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(catalyst, 1L, fuel, 1000L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(product, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(catalyst, 1L));
        for (long copies : new long[] { 1, 32 }) {
            var result = solve(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(catalyst),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(recipe),
                product,
                1_000_000,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(catalyst, copies),
                fuel);
            assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
            assertEquals(
                1L,
                result.requiredSeed()
                    .get(catalyst));
            assertEquals(
                1L,
                result.seedParallelism(result.requiredSeed())
                    .get(catalyst));
            assertEquals(
                1_000_000_000L,
                result.externalDemand()
                    .get(fuel));
            assertTrue(
                result.executionPlan()
                    .size() <= 2);
        }
    }

    @Test
    void largeReversibleConversionReportsTheWholeOrderAndOneReturnedCrystal() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            AEKey raw = mock(AEKey.class, "inferium"), product = mock(AEKey.class, "prudentium");
            AEKey crystal = mock(AEKey.class, "master_crystal");
            var upgrade = recipe(
                0,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 4L, crystal, 1L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(product, 1L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(crystal, 1L));
            var downgrade = recipe(
                1,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(product, 1L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 4L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
            var result = solve(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(raw, product),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(upgrade, downgrade),
                product,
                640_000L,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 19_314L),
                null);
            assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
            assertEquals(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 2_540_686L, crystal, 1L),
                result.seedShortfall());
            assertEquals(
                640_000L,
                result.patternTimes()
                    .get(upgrade.details()));
            assertTrue(
                result.diagnostics()
                    .stream()
                    .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.FULL_ORDER_MATERIAL_DEFICIT));
            var supplied = solve(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(raw, product),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(upgrade, downgrade),
                product,
                640_000L,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(raw, 2_560_000L, crystal, 1L),
                null);
            assertEquals(CycleSolveStatus.SUCCESS, supplied.status(), supplied.summary());
        });
    }

    @Test
    void splitMergeCircuitUsesBalanceEvenWithOneSearchState() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), c = mock(AEKey.class), product = mock(AEKey.class);
        var split = recipe(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 2L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 3L, c, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var merge = recipe(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 3L, c, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 2L, product, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var component = new CycleComponent(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a, b, c),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(split, merge),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var result = new BoundedCycleSolver().solve(
            new CycleSolveRequest(
                component,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(product, 1_000_000_000L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 2L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                new CycleSolveRequest.PlannerOptions(new CycleSolveLimits(8, 16, 1, 1, 0))),
            ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertEquals(
            1_000_000_000L,
            result.patternTimes()
                .get(split.details()));
        assertTrue(
            result.diagnostics()
                .stream()
                .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.STATE_EQUATION_WITNESS));
        assertTrue(
            result.executionPlan()
                .size() <= 3);
    }

    @Test
    void growingInternalInventoryCannotHideFiniteFuelInfeasibility() throws Exception {
        AEKey a = mock(AEKey.class), fuel = mock(AEKey.class), product = mock(AEKey.class);
        var grow = recipe(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 2L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var consume = recipe(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, fuel, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(product, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var result = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(grow, consume),
            product,
            5,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, fuel, 4L),
            null);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
        assertTrue(
            result.diagnostics()
                .stream()
                .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.STATE_EQUATION_INFEASIBLE));
        assertEquals(
            1L,
            result.seedShortfall()
                .get(fuel));
        assertTrue(
            result.diagnostics()
                .stream()
                .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.FULL_ORDER_MATERIAL_DEFICIT));
    }

    @Test
    void trillionProductsReuseOneBucketAndChargeEveryMillibucket() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            AEKey empty = AEItemKey.of(TestItems.BUCKET), full = AEItemKey.of(TestItems.WATER_BUCKET);
            AEKey water = AEFluidKey.of(TestFluids.WATER), product = AEItemKey.of(TestItems.CLAY_BALL);
            var fill = recipe(
                0,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(empty, 1L, water, 1000L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(full, 1L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
            var use = recipe(
                1,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(full, 1L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(product, 1L),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(empty, 1L));
            long quantity = 1_000_000_000_000L;
            var result = solve(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(empty, full),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(fill, use),
                product,
                quantity,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(empty, 1L),
                water);
            assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
            assertEquals(
                quantity,
                result.patternTimes()
                    .get(fill.details()));
            assertEquals(
                quantity,
                result.patternTimes()
                    .get(use.details()));
            assertEquals(
                quantity * 1000L,
                result.externalDemand()
                    .get(water));
            assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(empty, 1L), result.requiredSeed());
            assertTrue(
                result.executionPlan()
                    .size() <= 4,
                "A trillion laps must not become a trillion runs");
            assertTrue(
                result.executionPlan()
                    .stream()
                    .anyMatch(run -> run.repetitions() > 1));
            assertTrue(
                result.executionWitness()
                    .isEmpty());
        });
    }

    @Test
    void splitMergeByproductsAndReturnedCatalystFormOneExecutableCircuit() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), c = mock(AEKey.class);
        AEKey catalyst = AEItemKey.of(TestItems.DIAMOND), fuel = AEFluidKey.of(TestFluids.LAVA),
            product = mock(AEKey.class);
        var split = recipe(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 2L, catalyst, 1L, fuel, 1000L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 3L, c, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(catalyst, 1L));
        var merge = recipe(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 3L, c, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 2L, product, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var result = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a, b, c),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(split, merge),
            product,
            1_000_000_000L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 2L, catalyst, 1L),
            fuel);
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertEquals(
            1_000_000_000L,
            result.patternTimes()
                .get(split.details()));
        assertEquals(
            1L,
            result.requiredSeed()
                .get(catalyst));
        assertEquals(
            1_000_000_000_000L,
            result.externalDemand()
                .get(fuel));
        assertTrue(
            result.executionPlan()
                .size() < 10);
    }

    @Test
    void finiteFuelCannotBeInventedByRepeatingTheCarrierCircuit() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), fuel = mock(AEKey.class), product = mock(AEKey.class);
        var first = recipe(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, fuel, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var last = recipe(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, product, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var enough = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a, b),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(first, last),
            product,
            100,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, fuel, 100L),
            null);
        assertEquals(CycleSolveStatus.SUCCESS, enough.status(), enough.summary());
        assertEquals(
            100L,
            enough.requiredSeed()
                .get(fuel));
        var shortFuel = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a, b),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(first, last),
            product,
            100,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, fuel, 99L),
            null);
        assertNotEquals(CycleSolveStatus.SUCCESS, shortFuel.status());
        assertTrue(
            shortFuel.externalDemand()
                .isEmpty());
    }

    @Test
    void noCarrierCannotBootstrapFromBalancedOutputsAndKeepsBothSeedChoices() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), product = mock(AEKey.class);
        var first = recipe(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var last = recipe(
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, product, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var result = solve(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a, b),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(first, last),
            product,
            100,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            null);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
        assertTrue(
            result.startupCandidates()
                .contains(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L)));
        assertTrue(
            result.startupCandidates()
                .contains(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(b, 1L)));
    }

    private static CycleSolveResult solve(List<AEKey> members, List<CompiledPattern> patterns, AEKey product,
        long quantity, Map<AEKey, Long> stock, AEKey boundaryKey) throws Exception {
        var boundary = boundaryKey == null
            ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.<ComponentDependency>listOf()
            : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                new ComponentDependency(
                    0,
                    1,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                        new CraftingGraphEdge(
                            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(members),
                            boundaryKey,
                            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(patterns),
                            null))));
        var component = new CycleComponent(
            0,
            members,
            patterns,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            boundary);
        return new BoundedCycleSolver().solve(
            new CycleSolveRequest(
                component,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(product, quantity),
                stock,
                boundary,
                new CycleSolveRequest.PlannerOptions()),
            ECOCancellation.NONE);
    }

    private static CompiledPattern recipe(int id, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs,
        Map<AEKey, Long> returns) {
        var pattern = mock(IPatternDetails.class, "recipe" + id);
        var out = outputs.entrySet()
            .stream()
            .map(e -> new GenericStack(e.getKey(), e.getValue()))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        var returned = returns.entrySet()
            .stream()
            .map(e -> new GenericStack(e.getKey(), e.getValue()))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        when(pattern.getOutputs()).thenReturn(out);
        var semantics = new PatternSemantics(
            pattern,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            out,
            returned,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        return new CompiledPattern(
            id,
            pattern,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(out)
                .what(),
            PlannerAmount.of(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(out)
                    .amount()),
            inputs.entrySet()
                .stream()
                .map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            out,
            true,
            null,
            false,
            semantics);
    }
}
