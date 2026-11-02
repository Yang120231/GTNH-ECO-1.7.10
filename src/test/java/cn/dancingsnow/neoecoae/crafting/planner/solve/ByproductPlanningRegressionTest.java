package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledExecutionKernel;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

/**
 * Byproduct-heavy shapes from ore-processing style packs. AE2 advertises every output of a processing pattern as
 * craftable, so byproducts used to multiply producer candidates, structural cycles and fallback route attempts.
 */
class ByproductPlanningRegressionTest {

    private static AEKey[] items;

    @BeforeAll
    static void bootstrap() {
        if (items != null) return;
        InventoryTestBootstrap.initialize();
        // GregTech-style materials: one Item, distinguished by its damage value, as most GTNH ore products are.
        items = new AEKey[30];
        for (int i = 0; i < items.length; i++) items[i] = AEItemKey.of(new ItemStack(TestItems.PAPER, 1, i));
    }

    enum Shape {
        RING,
        MESH
    }

    /** Tier one cross-links neighbours as byproducts; MESH adds a tier that consumes tier one and returns some. */
    @com.github.bsideup.jabel.Desugar
    record Network(ICraftingService service, AEKey goal, AEKey[] raw) {}

    @Test
    void boundaryOnlyTransitionsDoNotInventUnrepresentableBatches() throws Exception {
        // Nine raw-only patterns sit inside the byproduct component. A boundary-only transition used to offer a
        // Long.MAX_VALUE batch and turned this 64-item order into a 1.5e20-firing witness.
        var network = network(Shape.MESH, 9, false);
        var result = plan(network, stock(network, 64), 64, new Counter(Long.MAX_VALUE));
        assertEquals(
            PlanningStatus.SUCCESS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        result.plan()
            .patternTimes()
            .values()
            .forEach(times -> assertTrue(times <= 64L * 4, "times=" + times));
    }

    @Test
    void byproductRingsMoveNoMaterialAndFormNoCycleComponent() throws Exception {
        var network = network(Shape.RING, 7, true);
        for (long rawEach : new long[] { 64, 16 }) {
            var counter = new Counter(Long.MAX_VALUE);
            var result = plan(network, stock(network, rawEach), 64, counter);
            assertEquals(
                rawEach == 64 ? PlanningStatus.SUCCESS : PlanningStatus.MISSING_ITEMS,
                result.status(),
                result.trace()
                    .diagnostics()
                    .toString());
            assertTrue(
                result.components()
                    .stream()
                    .noneMatch(component -> component.type() == ComponentPlanningResult.Type.CYCLIC),
                result.components()
                    .toString());
            assertTrue(counter.count < 100_000, "checkpoints=" + counter.count);
        }
    }

    @Test
    void infeasibleByproductMeshIsProvenWithoutSearchingRoutes() throws Exception {
        // AE2 advertised byproducts as producers here; 2,377 alternative routes then took 23 s (k=5) or the
        // whole session (k=7) before the order came back unresolved. Four raw items each cannot make 64 goals:
        // every tier-two firing nets one tier-one item, so at most 8k firings and 16k tops exist.
        for (int k : new int[] { 5, 7 }) {
            var network = network(Shape.MESH, k, true);
            var counter = new Counter(5_000_000L);
            var result = plan(network, stock(network, 4), 64, counter);
            List<PlannerDiagnostic.Code> codes = codes(result);
            // The tier-two recycle is a real (lossy) material loop, so the order may end on its cycle component.
            assertTrue(
                result.status() == PlanningStatus.MISSING_ITEMS || result.status() == PlanningStatus.PARTIAL,
                result.status() + " " + codes);
            assertTrue(
                codes.contains(PlannerDiagnostic.Code.MATERIAL_BALANCE_INFEASIBLE)
                    || codes.contains(PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND),
                codes.toString());
            assertFalse(codes.contains(PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED), codes.toString());
            assertTrue(counter.count < 200_000, "k=" + k + " checkpoints=" + counter.count);
        }
    }

    @Test
    void hardByproductMeshAnswersWithinAFractionOfTheSession() throws Exception {
        // Sixteen raw items each are enough on paper (32 tier-two firings each, every one yielding two tops), but
        // reaching it needs byproduct tops credited around the whole ring. The planner may answer PARTIAL; it must
        // answer quickly instead of enumerating producer combinations.
        for (int k : new int[] { 5, 7 }) {
            var network = network(Shape.MESH, k, true);
            var counter = new Counter(5_000_000L);
            var result = plan(network, stock(network, 16), 64, counter);
            List<PlannerDiagnostic.Code> codes = codes(result);
            assertNotEquals(PlanningStatus.CYCLE_UNRESOLVED, result.status(), codes.toString());
            assertFalse(codes.contains(PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED), codes.toString());
            assertTrue(counter.count < 200_000, "k=" + k + " checkpoints=" + counter.count);
        }
    }

    @Test
    void feasibleByproductMeshStillPlans() throws Exception {
        var network = network(Shape.MESH, 5, true);
        var result = plan(network, stock(network, 64), 64, new Counter(Long.MAX_VALUE));
        assertEquals(
            PlanningStatus.SUCCESS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
    }

    @Test
    void byproductProducersStayWhenNoPrimaryProducerCanBeFastPlanned() throws Exception {
        AEKey raw = AEItemKey.of(TestItems.RAW_IRON), main = AEItemKey.of(TestItems.GOLD_INGOT);
        AEKey side = AEItemKey.of(TestItems.IRON_NUGGET), goal = AEItemKey.of(TestItems.DIAMOND);
        // Q makes `main` and yields `side` as a byproduct; P makes `side` as its primary output.
        var q = pattern(
            JdkCompat.listOf(new GenericStack(raw, 1)),
            new GenericStack(main, 1),
            new GenericStack(side, 1));
        var top = pattern(JdkCompat.listOf(new GenericStack(side, 1)), new GenericStack(goal, 1));
        for (boolean plannable : new boolean[] { true, false }) {
            var p = pattern(JdkCompat.listOf(new GenericStack(raw, 1)), new GenericStack(side, 1));
            // A zero input multiplier is an invalid amount: the primary producer cannot be fast-planned.
            if (!plannable) when(p.getInputs()[0].getMultiplier()).thenReturn(0L);
            var network = new CraftingNetworkCompiler()
                .compile(service(true, JdkCompat.listOf(q, p, top)), goal, true, ECOCancellation.NONE);
            var producers = network.producers()
                .get(side);
            var primary = producers.stream()
                .filter(pattern -> pattern.details() == p)
                .findFirst()
                .orElseThrow(AssertionError::new);
            assertEquals(plannable, primary.fastSupported(), primary.unsupportedReason());
            // Only a plannable primary producer replaces the byproduct view; AE's producer order is kept otherwise.
            assertEquals(
                plannable ? JdkCompat.listOf(p) : JdkCompat.listOf(q, p),
                producers.stream()
                    .map(CompiledPattern::details)
                    .collect(Collectors.toList()));
            assertDoesNotThrow(() -> CompiledExecutionKernel.compile(network), "pattern ids must stay dense");
        }
    }

    static Network network(Shape shape, int k, boolean advertiseByproducts) {
        AEKey[] raw = new AEKey[k], mid = new AEKey[k];
        for (int j = 0; j < k; j++) {
            raw[j] = items[j];
            mid[j] = items[10 + j];
        }
        AEKey goal = AEItemKey.of(TestItems.DIAMOND);
        List<IPatternDetails> patterns = new ArrayList<>();
        for (int j = 0; j < k; j++) {
            patterns.add(
                pattern(
                    JdkCompat.listOf(new GenericStack(raw[j], 1)),
                    new GenericStack(mid[j], 1),
                    new GenericStack(mid[(j + 1) % k], 1)));
        }
        AEKey[] top = mid;
        if (shape == Shape.MESH) {
            top = new AEKey[k];
            for (int j = 0; j < k; j++) top[j] = items[20 + j];
            for (int j = 0; j < k; j++) {
                patterns.add(
                    pattern(
                        JdkCompat.listOf(new GenericStack(mid[j], 2)),
                        new GenericStack(top[j], 1),
                        new GenericStack(mid[(j + 2) % k], 1),
                        new GenericStack(top[(j + 1) % k], 1)));
            }
        }
        List<GenericStack> goalInputs = new ArrayList<>();
        for (int j = 0; j < k; j++) goalInputs.add(new GenericStack(top[j], 1));
        patterns.add(pattern(goalInputs, new GenericStack(goal, 1)));
        return new Network(service(advertiseByproducts, patterns), goal, raw);
    }

    static KeyCounter stock(Network network, long rawEach) {
        KeyCounter stock = new KeyCounter();
        for (AEKey raw : network.raw()) stock.add(raw, rawEach);
        return stock;
    }

    static ECOPlanningResult plan(Network network, KeyCounter stock, long amount, ECOCancellation cancellation)
        throws Exception {
        return new ECOCraftingPlannerService().createSession(network.service(), network.goal(), stock, true)
            .plan(amount, false, cancellation);
    }

    private static List<PlannerDiagnostic.Code> codes(ECOPlanningResult result) {
        return result.trace()
            .diagnostics()
            .stream()
            .map(PlannerDiagnostic::code)
            .collect(Collectors.toList());
    }

    static final class Counter implements ECOCancellation {

        private final long cap;
        long count;

        Counter(long cap) {
            this.cap = cap;
        }

        @Override
        public void checkpoint() throws InterruptedException {
            if (++count > cap) throw new InterruptedException("checkpoint cap");
        }
    }

    private static IPatternDetails pattern(List<GenericStack> inputs, GenericStack... outputs) {
        var pattern = mock(IPatternDetails.class);
        var raw = inputs.stream()
            .map(stack -> {
                var input = mock(IPatternDetails.IInput.class);
                when(input.getMultiplier()).thenReturn(1L);
                when(input.getPossibleInputs()).thenReturn(new GenericStack[] { stack });
                when(input.isValid(any(), isNull())).thenAnswer(
                    call -> stack.what()
                        .equals(call.getArgument(0)));
                return input;
            })
            .toArray(IPatternDetails.IInput[]::new);
        when(pattern.getInputs()).thenReturn(raw);
        when(pattern.getOutputs()).thenReturn(JdkCompat.listOf(outputs));
        when(pattern.getPrimaryOutput()).thenReturn(outputs[0]);
        return pattern;
    }

    private static ICraftingService service(boolean byproducts, List<IPatternDetails> patterns) {
        Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
        for (var pattern : patterns) {
            for (var output : byproducts ? pattern.getOutputs() : JdkCompat.listOf(pattern.getPrimaryOutput())) {
                var list = index.computeIfAbsent(output.what(), ignored -> new ArrayList<>());
                if (!list.contains(pattern)) list.add(pattern);
            }
        }
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any()))
            .thenAnswer(call -> index.getOrDefault(call.getArgument(0), JdkCompat.listOf()));
        return service;
    }
}
