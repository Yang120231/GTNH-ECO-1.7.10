package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

class SeedFloorsBudgetTest {

    private final AEKey a = key("A"), b = key("B"), f = key("F"), x = key("X");
    // X <- A; A + F -> 2B; B -> A. The {A, B} cycle grows from one A and cannot restart without it.
    private final CompiledPattern px = pattern(0, x, 1, new GenericStack(a, 1));
    private final CompiledPattern p1 = pattern(1, b, 2, new GenericStack(a, 1), new GenericStack(f, 1));
    private final CompiledPattern p2 = pattern(2, a, 1, new GenericStack(b, 1));

    /**
     * The growth balance and the minimum-seed probe run on slices of the session budget. A slice that runs out
     * leaves the seed undetermined; only the session's own limit may end the calculation.
     */
    @Test
    void exhaustedProbeSlicesNeverEndTheSessionEarly() throws Exception {
        boolean undetermined = false;
        SeedFloors measured = null;
        for (long work = 1L; work <= 1_000_000L && measured == null; work = work < 256L ? work + 1L : work * 5L / 4L) {
            // A new compiled network each time: decided seed profiles are cached per network.
            var network = network();
            var session = new ECOPlanningBudget(ECOCancellation.NONE, work, Long.MAX_VALUE, () -> 0L);
            SeedFloors floors;
            try {
                floors = SeedFloors.compute(network, cycles(network), stock(), new BoundedCycleSolver(), session);
            } catch (ECOPlanningBudget.Exhausted exhausted) {
                assertThrows(
                    ECOPlanningBudget.Exhausted.class,
                    session::checkpoint,
                    "A probe slice ended the session with work to spare; maxWork=" + work);
                continue;
            }
            if (!floors.undetermined()
                .isEmpty()) undetermined = true;
            if (!floors.floors()
                .isEmpty()) measured = floors;
        }
        assertTrue(undetermined, "Small sessions must report the seed as undetermined");
        assertNotNull(measured, "A large enough session measures the seed");
        assertEquals(
            1,
            measured.floors()
                .size());
        assertEquals(
            a,
            measured.floors()
                .get(0)
                .key());
        assertEquals(
            PlannerAmount.of(1L),
            measured.floors()
                .get(0)
                .minimum());
        assertEquals(
            PlannerAmount.of(1L),
            measured.floors()
                .get(0)
                .reserved());
    }

    /** An undecided growth balance is not cached as "needs no seed": the next order with room keeps the seed. */
    @Test
    void undeterminedSeedIsMeasuredAgainByTheNextOrder() throws Exception {
        var network = network();
        var cycles = cycles(network);
        var tiny = new ECOPlanningBudget(ECOCancellation.NONE, 12L, Long.MAX_VALUE, () -> 0L);
        try {
            var starved = SeedFloors.compute(network, cycles, stock(), new BoundedCycleSolver(), tiny);
            assertTrue(
                starved.floors()
                    .isEmpty());
        } catch (ECOPlanningBudget.Exhausted exhausted) {
            assertThrows(ECOPlanningBudget.Exhausted.class, tiny::checkpoint);
        }
        var floors = SeedFloors.compute(network, cycles, stock(), new BoundedCycleSolver(), ECOCancellation.NONE);
        assertEquals(
            1,
            floors.floors()
                .size(),
            floors.toString());
        assertEquals(
            a,
            floors.floors()
                .get(0)
                .key());
        assertTrue(
            floors.undetermined()
                .isEmpty());
    }

    private CompiledNetwork network() {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(x, JdkCompat.listOf(px));
        producers.put(b, JdkCompat.listOf(p1));
        producers.put(a, JdkCompat.listOf(p2));
        producers.put(f, JdkCompat.listOf());
        return new CompiledNetwork(x, producers, JdkCompat.setOf(), 3, 4);
    }

    private static List<CycleComponent> cycles(CompiledNetwork network) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        return CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE)
            .cycles();
    }

    private PlannerInventorySnapshot stock() {
        var inventory = new KeyCounter();
        inventory.add(a, 5L);
        inventory.add(f, 10L);
        return PlannerInventorySnapshot.of(inventory);
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(int id, AEKey output, long amount, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        List<GenericStack> outputs = JdkCompat.listOf(new GenericStack(output, amount));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(
            details,
            null,
            JdkCompat.listOf(),
            outputs,
            JdkCompat.listOf(),
            JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        return new CompiledPattern(
            id,
            details,
            output,
            PlannerAmount.of(amount),
            Arrays.stream(inputs)
                .map(i -> new CompiledInput(null, i.what(), i.amount(), true, null))
                .collect(Collectors.toList()),
            outputs,
            true,
            null,
            false,
            semantics);
    }
}
