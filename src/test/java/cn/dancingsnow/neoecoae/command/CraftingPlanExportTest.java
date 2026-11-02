package cn.dancingsnow.neoecoae.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageServiceView;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

class CraftingPlanExportTest {

    private static AEKey seed, product, fuel, rawLeft, rawRight, left, right, slag, goal;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        seed = AEItemKey.of(TestItems.EMERALD);
        product = AEItemKey.of(TestItems.SUGAR);
        fuel = AEItemKey.of(TestItems.COAL);
        rawLeft = AEItemKey.of(TestItems.RAW_IRON);
        rawRight = AEItemKey.of(TestItems.RAW_COPPER);
        left = AEItemKey.of(TestItems.IRON_INGOT);
        right = AEItemKey.of(TestItems.COPPER_INGOT);
        slag = AEItemKey.of(TestItems.GRAVEL);
        goal = AEItemKey.of(TestItems.DIAMOND);
    }

    @Test
    void exportsPlannerStateCyclesProducerOrderAndSurplus() throws Exception {
        // Growth loop: seed + fuel -> 2 product, product -> seed.
        var grow = pattern(JdkCompat.listOf(stack(seed, 1), stack(fuel, 1)), stack(product, 2));
        var back = pattern(JdkCompat.listOf(stack(product, 1)), stack(seed, 1));
        // Byproduct ring without a material loop, plus a byproduct no pattern consumes.
        var makeLeft = pattern(JdkCompat.listOf(stack(rawLeft, 1)), stack(left, 1), stack(right, 1), stack(slag, 1));
        var makeRight = pattern(JdkCompat.listOf(stack(rawRight, 1)), stack(right, 1), stack(left, 1));
        var finish = pattern(JdkCompat.listOf(stack(product, 1), stack(left, 1), stack(right, 1)), stack(goal, 1));
        var service = service(grow, back, makeLeft, makeRight, finish);
        KeyCounter stock = new KeyCounter();
        stock.add(seed, 1);
        stock.add(fuel, 64);
        stock.add(rawLeft, 64);
        stock.add(rawRight, 64);

        var result = new ECOCraftingPlannerService().createSession(service, goal, stock, true)
            .plan(4, false, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.SUCCESS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());

        String text = CraftingPlanExport
            .render(grid(service, stock), result.plan(), result, new ECOPlannerOptions(true, false, JdkCompat.setOf()));

        assertTrue(text.contains("options: cyclePlanning=true ignoreSubstitutions=false fuzzyItems=[]"), text);
        assertTrue(text.contains("result: status=SUCCESS calculationMs="), text);
        assertTrue(text.contains("diagnostics:"), text);
        // left/right each have a primary producer first and a byproduct producer second.
        assertTrue(text.contains("minecraft:iron_ingot: P"), text);
        assertTrue(text.matches("(?s).*minecraft:iron_ingot: P\\d+ > P\\d+\\*.*"), text);
        // The seed loop is a real growth recipe; the byproduct ring only orders byproducts.
        assertTrue(text.matches("(?s).*S\\d+ RECIPE GROWTH maxGain=x2 .*"), text);
        assertTrue(text.matches("(?s).*S\\d+ ORDER_ONLY NO_MATERIAL_LOOP .*"), text);
        assertTrue(text.contains("~P"), text);
        assertTrue(text.matches("(?s).*used=\\d+ avail=\\d+.*minecraft:coal.*"), text);
        assertTrue(text.matches("(?s).*\\n\\d+ minecraft:gravel \\[sink\\] \\[byproduct\\]\\n.*"), text);
        assertTrue(text.contains("## CRAFTS"), text);
        assertTrue(text.matches("(?s).*  stock:.* surplus=-?\\d+;.*"), text);
    }

    @Test
    void nativePlanWithoutItsTaskListLeavesOutTheTaskSections() {
        var make = pattern(JdkCompat.listOf(stack(fuel, 2)), stack(product, 1));
        KeyCounter stock = new KeyCounter();
        stock.add(fuel, 10);
        KeyCounter used = new KeyCounter();
        used.add(fuel, 4);
        var plan = new CraftingPlan(
            stack(product, 2),
            0L,
            false,
            false,
            used,
            new KeyCounter(),
            new KeyCounter(),
            JdkCompat.mapOf());

        String text = CraftingPlanExport.render(grid(service(make), stock), plan, null, null, false);

        assertTrue(text.contains("options: unknown (the request carried no ECO planner options)"), text);
        assertTrue(text.contains("result: no ECO planning result attached"), text);
        assertTrue(
            text.matches("(?s).*## PATTERNS \\(1\\)\\nP1 \\[[^\\]]+\\] 2 minecraft:coal -> 1 minecraft:sugar\\n.*"),
            text);
        assertTrue(text.contains("used=4 avail=10 minecraft:coal"), text);
        assertFalse(text.contains("## CRAFTS"), text);
        assertFalse(text.contains("## SURPLUS"), text);
        assertFalse(text.contains("## IMBALANCED"), text);

        // The same plan with its executions known reads like any other plan.
        var known = new CraftingPlan(
            stack(product, 2),
            0L,
            false,
            false,
            used,
            new KeyCounter(),
            new KeyCounter(),
            JdkCompat.mapOf(make, 2L));
        String complete = CraftingPlanExport.render(grid(service(make), stock), known, null, null, true);
        assertTrue(
            complete.contains("## CRAFTS (pattern x times, top-down from goal)\nP1 x2  -> minecraft:sugar\n"),
            complete);
    }

    @Test
    void itemKeysCarryTheirDamageAndTagNbtThroughTheLegend() {
        AEKey damaged = AEItemKey.of(new ItemStack(TestItems.PAPER, 1, 3));
        ItemStack tagged = new ItemStack(TestItems.PAPER);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("charge", 7);
        tagged.setTagCompound(nbt);
        AEKey withNbt = AEItemKey.of(tagged);
        var make = pattern(JdkCompat.listOf(stack(damaged, 1)), stack(withNbt, 1));
        var plan = new CraftingPlan(
            stack(withNbt, 1),
            0L,
            true,
            false,
            new KeyCounter(),
            new KeyCounter(),
            new KeyCounter(),
            JdkCompat.mapOf(make, 1L));

        String text = CraftingPlanExport.render(grid(service(make), new KeyCounter()), plan, null, null);

        assertTrue(text.contains("goal: 1 minecraft:paper#"), text);
        assertTrue(text.matches("(?s).*\\] 1 minecraft:paper:3 -> 1 minecraft:paper#[0-9a-f]{4}\\n.*"), text);
        assertTrue(text.matches("(?s).*## KEYS\\nminecraft:paper#[0-9a-f]{4} = .*charge:7.*"), text);
        assertTrue(text.contains("simulation: true (missing inputs, not executable)"), text);
    }

    private static GridView grid(ICraftingService service, KeyCounter stock) {
        var storage = mock(StorageServiceView.class);
        when(storage.getCachedInventory()).thenReturn(stock);
        var grid = mock(GridView.class);
        when(grid.getCraftingService()).thenReturn(service);
        when(grid.getStorageService()).thenReturn(storage);
        return grid;
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

    /** AE2 advertises every output of a pattern, so byproducts become craftable keys too. */
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
        when(service.getCraftables(any())).thenReturn(index.keySet());
        return service;
    }
}
