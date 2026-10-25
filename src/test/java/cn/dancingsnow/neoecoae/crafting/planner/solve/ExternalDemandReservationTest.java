package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ExternalDemandReservationTest {

    private final AEKey material = mock(AEKey.class);
    private final CycleComponent cycle = new CycleComponent(
        0,
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(material),
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
    private final CompiledNetwork network = new CompiledNetwork(
        material,
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
            .mapOf(material, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
        0,
        0);

    @Test
    void reservationShortageSubtractsTheRemainingStock() throws Exception {
        var inventory = new KeyCounter();
        inventory.add(material, 3L);
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            inventory,
            new SolveState(inventory),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(material, 5L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertEquals(CycleExternalDemandStatus.MISSING, result.status());
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(material, 2L), result.missingLeaves());
        assertEquals(3L, inventory.get(material));
    }

    @Test
    void unboundedSnapshotSupplyDoesNotRequireAFiniteInventoryEntry() throws Exception {
        when(material.getAmountPerByte()).thenReturn(8);
        var inventory = new KeyCounter();
        var base = new SolveState(
            PlannerInventorySnapshot.of(inventory, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(material)));
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(
            network,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(material, 100L),
            inventory,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertEquals(CycleExternalDemandStatus.SOLVED, result.status());
        assertEquals(
            100L,
            result.directReservations()
                .get(material));
        assertTrue(
            result.states()
                .isEmpty());
    }

    @Test
    void boundaryRootsShareAnUnadvertisedByproductWithoutRepeatingThePhysicalPattern() throws Exception {
        AEKey raw = key(), a = key(), b = key();
        var joint = pattern(
            a,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(raw, 1)),
            new GenericStack(a, 1),
            new GenericStack(b, 1));
        var graph = new CompiledNetwork(
            a,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                a,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(joint),
                b,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                raw,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            1,
            1);
        var stock = new KeyCounter();
        stock.add(raw, 1);
        Map<AEKey, Long> roots = new LinkedHashMap<>();
        roots.put(b, 1L);
        roots.put(a, 1L);
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(
            graph,
            cycle,
            roots,
            stock,
            new SolveState(stock),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertTrue(result.solved(), result.diagnostic());
        assertEquals(
            1,
            result.states()
                .size());
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(joint.details(), 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(result.states())
                .patternTimes());
        assertEquals(
            1L,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(result.states())
                .usedItems()
                .get(raw));
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(result.states())
            .executionProvenance()
            .requireComplete();
        assertEquals(1L, stock.get(raw));
    }

    @Test
    void boundaryRootsCannotBothSpendTheSameFiniteRawMaterial() throws Exception {
        AEKey raw = key(), a = key(), b = key();
        var pa = pattern(
            a,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(raw, 1)),
            new GenericStack(a, 1));
        var pb = pattern(
            b,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(raw, 1)),
            new GenericStack(b, 1));
        var graph = new CompiledNetwork(
            a,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                a,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pa),
                b,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pb),
                raw,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            2,
            2);
        var stock = new KeyCounter();
        stock.add(raw, 1);
        var base = new SolveState(stock);
        var result = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(
            graph,
            cycle,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, 1L, b, 1L),
            stock,
            base,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
            ECOCancellation.NONE);
        assertEquals(CycleExternalDemandStatus.MISSING, result.status());
        assertEquals(
            1L,
            result.missingLeaves()
                .get(raw));
        assertTrue(
            base.patternTimes()
                .isEmpty());
        assertTrue(
            base.usedItems()
                .isEmpty());
    }

    private static AEKey key() {
        AEKey key = mock(AEKey.class);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(AEKey primary, List<GenericStack> inputs, GenericStack... outputs) {
        var details = mock(IPatternDetails.class);
        var outputList = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(outputs);
        when(details.getOutputs()).thenReturn(outputList);
        var semantics = new PatternSemantics(
            details,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            outputList,
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
            primary,
            PlannerAmount.of(outputs[0].amount()),
            inputs.stream()
                .map(input -> new CompiledInput(null, input.what(), input.amount(), true, null))
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList()),
            outputList,
            true,
            null,
            false,
            semantics);
    }
}
