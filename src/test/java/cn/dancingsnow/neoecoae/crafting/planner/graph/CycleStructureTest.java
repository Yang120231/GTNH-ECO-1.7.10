package cn.dancingsnow.neoecoae.crafting.planner.graph;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.AEKeyFilter;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Structural SCC readings on small compiled networks, including 1.7.10 items that share one {@code Item}. */
class CycleStructureTest {

    private static AEKey x, goal;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        x = AEItemKey.of(TestItems.SAND);
        goal = AEItemKey.of(TestItems.GLASS);
    }

    @Test
    void growthSeedLoopIsARecipeWithItsGain() throws Exception {
        AEKey seed = AEItemKey.of(TestItems.EMERALD), product = AEItemKey.of(TestItems.SUGAR),
            fuel = AEItemKey.of(TestItems.COAL);
        var grow = pattern(JdkCompat.listOf(stack(seed, 1), stack(fuel, 1)), stack(product, 2));
        var back = pattern(JdkCompat.listOf(stack(product, 1)), stack(seed, 1));
        var finish = pattern(JdkCompat.listOf(stack(product, 1)), stack(goal, 1));
        CycleComponent cycle = onlyCycle(service(grow, back, finish), goal, JdkCompat.setOf());

        var shape = CycleStructure.classify(cycle.members(), cycle.internalEdges(), 4);
        assertEquals(CycleStructure.Kind.RECIPE, shape.kind());
        assertEquals(CycleStructure.Growth.GROWTH, shape.growth());
        assertEquals("x2", String.valueOf(shape.maxGain()));
        assertTrue(CycleStructure.materialLoop(cycle.members(), cycle.internalEdges()));
        assertTrue(
            CycleStructure.redundantOrderingEdges(cycle.members(), cycle.internalEdges())
                .isEmpty());
    }

    @Test
    void chainedByproductOrderingIsNoMaterialLoop() throws Exception {
        AEKey rawLeft = AEItemKey.of(TestItems.RAW_IRON), rawRight = AEItemKey.of(TestItems.RAW_COPPER),
            left = AEItemKey.of(TestItems.IRON_INGOT), right = AEItemKey.of(TestItems.COPPER_INGOT);
        var makeLeft = pattern(JdkCompat.listOf(stack(rawLeft, 1)), stack(left, 1), stack(right, 1));
        var makeRight = pattern(JdkCompat.listOf(stack(rawRight, 1)), stack(right, 1), stack(left, 1));
        var finish = pattern(JdkCompat.listOf(stack(left, 1), stack(right, 1)), stack(goal, 1));
        CycleComponent cycle = onlyCycle(service(makeLeft, makeRight, finish), goal, JdkCompat.setOf());

        var shape = CycleStructure.classify(cycle.members(), cycle.internalEdges(), 4);
        assertEquals(CycleStructure.Kind.ORDER_ONLY, shape.kind());
        assertEquals(CycleStructure.Growth.NO_MATERIAL_LOOP, shape.growth());
        assertFalse(CycleStructure.materialLoop(cycle.members(), cycle.internalEdges()));
        Set<CraftingGraphEdge> ordering = new LinkedHashSet<>();
        for (CraftingGraphEdge edge : cycle.internalEdges())
            if (CycleStructure.kind(edge) == CycleStructure.EdgeKind.ORDERING) ordering.add(edge);
        assertFalse(ordering.isEmpty());
        assertEquals(ordering, CycleStructure.redundantOrderingEdges(cycle.members(), cycle.internalEdges()));
    }

    @Test
    void byproductOfTheSameMetaItemKeepsItsByproductReading() throws Exception {
        // GregTech-style meta items: primary and byproduct share one Item and differ only in damage.
        AEKey primary = AEItemKey.of(new ItemStack(TestItems.PAPER, 1, 1));
        AEKey byproduct = AEItemKey.of(new ItemStack(TestItems.PAPER, 1, 2));
        var split = pattern(JdkCompat.listOf(stack(x, 1)), stack(primary, 1), stack(byproduct, 1));
        var merge = pattern(JdkCompat.listOf(stack(byproduct, 1)), stack(x, 2));
        var finish = pattern(JdkCompat.listOf(stack(x, 1)), stack(goal, 1));
        CycleComponent cycle = onlyCycle(service(split, merge, finish), goal, JdkCompat.setOf());

        assertEquals(
            CycleStructure.Kind.BYPRODUCT_VIEW,
            CycleStructure.classify(cycle.members(), cycle.internalEdges(), 4)
                .kind());
        CraftingGraphEdge viaByproduct = edge(cycle, byproduct, x);
        assertEquals(CycleStructure.EdgeKind.CONSUMPTION, CycleStructure.kind(viaByproduct));
        assertFalse(CycleStructure.primaryView(viaByproduct));
        assertTrue(CycleStructure.primaryView(edge(cycle, x, byproduct)));
    }

    @Test
    void componentInsensitiveViewOfThePrimaryStaysAPrimaryView() throws Exception {
        // The dependency asks for paper:0; the only producer makes paper:5 and is renamed to the dependency key.
        AEKey wanted = AEItemKey.of(new ItemStack(TestItems.PAPER, 1, 0));
        AEKey made = AEItemKey.of(new ItemStack(TestItems.PAPER, 1, 5));
        var make = pattern(JdkCompat.listOf(stack(x, 1)), stack(made, 2));
        var use = pattern(JdkCompat.listOf(stack(wanted, 1)), stack(x, 1));
        var finish = pattern(JdkCompat.listOf(stack(x, 1)), stack(goal, 1));
        CycleComponent cycle = onlyCycle(
            service(make, use, finish),
            goal,
            JdkCompat.setOf(ResourceLocation.parse("minecraft:paper")));

        assertTrue(
            cycle.members()
                .contains(wanted));
        CraftingGraphEdge aliased = edge(cycle, wanted, x);
        assertTrue(CycleStructure.primaryView(aliased));
        var shape = CycleStructure.classify(cycle.members(), cycle.internalEdges(), 4);
        assertEquals(CycleStructure.Kind.RECIPE, shape.kind());
        assertEquals(CycleStructure.Growth.GROWTH, shape.growth());
    }

    private static CycleComponent onlyCycle(ICraftingService service, AEKey target, Set<ResourceLocation> fuzzy)
        throws InterruptedException {
        var network = new CraftingNetworkCompiler().compile(service, target, true, fuzzy, ECOCancellation.NONE);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph
            .build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        List<CycleComponent> cycles = condensation.cycles();
        assertEquals(1, cycles.size(), cycles.toString());
        return cycles.get(0);
    }

    private static CraftingGraphEdge edge(CycleComponent cycle, AEKey producer, AEKey input) {
        for (CraftingGraphEdge edge : cycle.internalEdges()) if (edge.producer()
            .equals(producer)
            && edge.requiredInput()
                .equals(input))
            return edge;
        throw new AssertionError("No edge " + producer + " -> " + input + " in " + cycle.internalEdges());
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
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

    /** Every output is advertised, as AE2 does, and the craftable listing honours its filter. */
    private static ICraftingService service(IPatternDetails... patterns) {
        Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
        for (var pattern : patterns) {
            for (var output : pattern.getOutputs()) {
                var list = index.computeIfAbsent(output.what(), ignored -> new ArrayList<>());
                if (!list.contains(pattern)) list.add(pattern);
            }
        }
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any()))
            .thenAnswer(call -> index.getOrDefault(call.getArgument(0), JdkCompat.listOf()));
        when(service.getCraftables(any())).thenAnswer(call -> {
            AEKeyFilter filter = call.getArgument(0);
            Set<AEKey> matching = new LinkedHashSet<>();
            for (AEKey key : index.keySet()) if (filter.matches(key)) matching.add(key);
            return matching;
        });
        return service;
    }
}
