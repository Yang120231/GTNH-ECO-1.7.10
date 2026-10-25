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

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItemRegistry;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.AEKeyFilter;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

class ByproductPlanningTest {

    private static AEKey ore;
    private static AEKey ingot;
    private static AEKey slag;
    private static AEKey brick;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        ore = AEItemKey.of(TestItems.RAW_IRON);
        ingot = AEItemKey.of(TestItems.IRON_INGOT);
        slag = AEItemKey.of(TestItems.GRAVEL);
        brick = AEItemKey.of(TestItems.BRICK);
    }

    @Test
    void testCraftPrimaryOutputWithByproduct() throws Exception {
        var pattern = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 3)),
            stack(ingot, 2),
            stack(slag, 3));
        var result = plan(indexedService(false, pattern), ingot, 5, 9);
        assertSuccessful(result, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 3L), 9);
    }

    @Test
    void testCraftByproductDirectly() throws Exception {
        var pattern = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 2)),
            stack(ingot, 1),
            stack(slag, 3));
        var result = plan(indexedService(true, pattern), slag, 7, 6);
        assertSuccessful(result, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 3L), 6);
    }

    @Test
    void testCraftIntermediateByproductChain() throws Exception {
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 2)),
            stack(ingot, 1),
            stack(slag, 3));
        var downstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(slag, 2)),
            stack(brick, 1));
        var result = plan(indexedService(true, upstream, downstream), brick, 5, 8);
        assertSuccessful(result, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(upstream, 4L, downstream, 5L), 8);
    }

    @Test
    void testJointConsumptionOfPrimaryAndByproduct() throws Exception {
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 1)),
            stack(ingot, 1),
            stack(slag, 1));
        for (var inputs : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ingot, 1), stack(slag, 1)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(slag, 1), stack(ingot, 1)))) {
            var downstream = pattern(inputs, stack(brick, 1));
            var service = indexedService(true, upstream, downstream);
            var network = new CraftingNetworkCompiler().compile(service, brick, ECOCancellation.NONE);
            var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
            assertTrue(
                new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE)
                    .stream()
                    .noneMatch(component -> component.cyclic()));
            assertSuccessful(
                plan(service, brick, 5, 5),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(upstream, 5L, downstream, 5L),
                5);
        }
    }

    @Test
    void testMultipleStacksOfSameOutput() throws Exception {
        var pattern = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 1)),
            stack(ingot, 2),
            stack(ingot, 1));
        assertSuccessful(
            plan(indexedService(false, pattern), ingot, 7, 3),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 3L),
            3);
    }

    @Test
    void matchingOutputTotalCanExceedLongWithoutOverflow() throws Exception {
        var pattern = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 1)),
            stack(ingot, Long.MAX_VALUE),
            stack(ingot, Long.MAX_VALUE));
        var service = indexedService(false, pattern);
        var network = new CraftingNetworkCompiler().compile(service, ingot, ECOCancellation.NONE);
        assertEquals(
            PlannerAmount.of(Long.MAX_VALUE)
                .multiply(2L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(network.producersOf(ingot))
                .outputPerPattern());
        assertSuccessful(
            plan(service, ingot, Long.MAX_VALUE / 2, 1),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 1L),
            1);
    }

    @Test
    void primaryOutputDoesNotHaveToBeTheFirstStack() throws Exception {
        var pattern = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 1)),
            stack(ingot, 2),
            stack(slag, 1));
        when(pattern.getOutputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(slag, 1), stack(ingot, 2)));
        assertSuccessful(
            plan(indexedService(false, pattern), ingot, 3, 2),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 2L),
            2);
    }

    @Test
    void componentInsensitiveByproductStacksAreSummedAndCredited() throws Exception {
        ItemStack namedGravel = new ItemStack(TestItems.GRAVEL);
        cn.dancingsnow.neoecoae.platform.nbt.CellComponents
            .writeCustomName(namedGravel, Component.literal("byproduct"));
        AEKey namedSlag = AEItemKey.of(namedGravel);
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 1)),
            stack(ingot, 1),
            stack(namedSlag, 2),
            stack(slag, 1),
            stack(namedSlag, 1));
        var downstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(slag, 1)),
            stack(brick, 1));
        // Component-insensitive planning still requires the physical recipe to accept the concrete key.
        when(downstream.getInputs()[0].isValid(any(), isNull())).thenAnswer(
            invocation -> invocation.getArgument(0) instanceof AEItemKey item && item.getItem() == TestItems.GRAVEL);
        var service = indexedService(true, upstream, downstream);
        var stock = new KeyCounter();
        stock.add(ore, 2);
        var result = new ECOCraftingPlannerService()
            .createSession(
                service,
                brick,
                stock,
                false,
                false,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(TestItemRegistry.ITEM.getKey(TestItems.GRAVEL)))
            .plan(7, false, ECOCancellation.NONE);
        assertSuccessful(result, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(upstream, 2L, downstream, 7L), 2);
        assertEquals(
            namedSlag,
            upstream.getOutputs()
                .get(1)
                .what());
    }

    @Test
    void componentInsensitivePlanningCannotOverrideThePhysicalInputPredicate() throws Exception {
        ItemStack namedGravel = new ItemStack(TestItems.GRAVEL);
        cn.dancingsnow.neoecoae.platform.nbt.CellComponents
            .writeCustomName(namedGravel, Component.literal("rejected by exact recipe"));
        AEKey namedSlag = AEItemKey.of(namedGravel);
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 1)),
            stack(ingot, 1),
            stack(namedSlag, 4));
        var downstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(slag, 1)),
            stack(brick, 1));
        var stock = new KeyCounter();
        stock.add(ore, 1);
        var result = new ECOCraftingPlannerService()
            .createSession(
                indexedService(true, upstream, downstream),
                brick,
                stock,
                false,
                false,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(TestItemRegistry.ITEM.getKey(TestItems.GRAVEL)))
            .plan(4, false, ECOCancellation.NONE);
        assertNotEquals(PlanningStatus.SUCCESS, result.status());
    }

    @Test
    void ae2PrimaryOnlyIndexDoesNotOfferAStandaloneByproductRecipe() throws Exception {
        var pattern = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 1)),
            stack(ingot, 1),
            stack(slag, 1));
        var result = plan(indexedService(false, pattern), slag, 1, 1);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertTrue(
            result.exactPatternTimes()
                .isEmpty());
        assertEquals(
            PlannerAmount.ONE,
            result.exactMissingItems()
                .get(slag));
    }

    @Test
    void unindexedPrimaryOutputStillClosesRealFeedbackForAByproductTarget() throws Exception {
        // This service offers only the secondary output. Its input needs the primary output
        // returned by the same physical recipe, so credit cannot bootstrap a seed-free execution.
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore, 1)),
            stack(ingot, 1),
            stack(slag, 1));
        var convert = pattern(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ingot, 1)), stack(ore, 1));
        var service = indexedService(false, convert);
        when(service.getCraftingFor(slag)).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(upstream));
        var network = new CraftingNetworkCompiler().compile(service, slag, ECOCancellation.NONE);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        assertTrue(
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE)
                .stream()
                .anyMatch(component -> component.cyclic()),
            "A real seed feedback loop must remain visible");
        var result = plan(service, slag, 1, 0);
        assertNotEquals(PlanningStatus.SUCCESS, result.status());
        assertFalse(
            result.cycles()
                .isEmpty());
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static IPatternDetails pattern(List<GenericStack> inputs, GenericStack... outputs) {
        var details = mock(IPatternDetails.class);
        var rawInputs = inputs.stream()
            .map(stack -> {
                var input = mock(IPatternDetails.IInput.class);
                when(input.getMultiplier()).thenReturn(1L);
                when(input.getPossibleInputs()).thenReturn(new GenericStack[] { stack });
                when(input.isValid(any(), isNull())).thenAnswer(
                    invocation -> stack.what()
                        .equals(invocation.getArgument(0)));
                return input;
            })
            .toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(rawInputs);
        when(details.getOutputs()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(outputs));
        when(details.getPrimaryOutput()).thenReturn(outputs[0]);
        return details;
    }

    /** AE2 indexes only primary outputs; an extended service may also offer byproduct views. */
    private static ICraftingService indexedService(boolean indexByproducts, IPatternDetails... patterns) {
        var index = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (var pattern : patterns) {
            var outputs = indexByproducts ? pattern.getOutputs()
                : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pattern.getPrimaryOutput());
            for (var output : outputs) {
                var candidates = index.computeIfAbsent(output.what(), ignored -> new ArrayList<>());
                if (!candidates.contains(pattern)) candidates.add(pattern);
            }
        }
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(
            invocation -> index
                .getOrDefault(invocation.getArgument(0), cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        when(service.getCraftables(any())).thenAnswer(invocation -> {
            AEKeyFilter filter = invocation.getArgument(0);
            return index.keySet()
                .stream()
                .filter(filter::matches)
                .collect(Collectors.toSet());
        });
        return service;
    }

    private static ECOPlanningResult plan(ICraftingService service, AEKey goal, long amount, long oreStock)
        throws Exception {
        var inventory = new KeyCounter();
        if (oreStock > 0) inventory.add(ore, oreStock);
        return new ECOCraftingPlannerService().createSession(service, goal, inventory, false)
            .plan(amount, false, ECOCancellation.NONE);
    }

    private static void assertSuccessful(ECOPlanningResult result, Map<IPatternDetails, Long> firings, long usedOre) {
        assertEquals(
            PlanningStatus.SUCCESS,
            result.status(),
            () -> result.trace()
                .diagnostics()
                .toString());
        assertEquals(
            firings,
            result.plan()
                .patternTimes());
        assertEquals(
            PlannerAmount.of(usedOre),
            result.exactUsedItems()
                .get(ore));
        assertTrue(
            result.exactMissingItems()
                .isEmpty());
    }
}
