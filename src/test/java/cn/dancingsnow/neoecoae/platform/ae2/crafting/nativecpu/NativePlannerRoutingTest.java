package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Requests reaching a pattern the ECO planner cannot model are planned (and executed) by AE2 itself. */
class NativePlannerRoutingTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private final Map<AEKey, Collection<ICraftingPatternDetails>> catalog = new HashMap<>();

    private static IAEItemStack stack(Item item) {
        IAEItemStack stack = mock(IAEItemStack.class);
        when(stack.getItem()).thenReturn(item);
        when(stack.getItemDamage()).thenReturn(0);
        when(stack.getItemStack()).thenReturn(new ItemStack(item));
        when(stack.copy()).thenReturn(stack);
        return stack;
    }

    private ICraftingPatternDetails produce(IAEItemStack output, boolean craftable, boolean substitute,
        IAEItemStack... inputs) {
        ICraftingPatternDetails pattern = mock(ICraftingPatternDetails.class);
        when(pattern.isCraftable()).thenReturn(craftable);
        when(pattern.canSubstitute()).thenReturn(substitute);
        when(pattern.getCondensedAEInputs()).thenReturn(inputs);
        catalog.computeIfAbsent(AEKey.fromNative(output), ignored -> new java.util.ArrayList<>())
            .add(pattern);
        return pattern;
    }

    private String reason(IAEItemStack goal) {
        return NativePlannerRouting.nativeOnlyReason((IAEStack<?> stack) -> catalog.get(AEKey.fromNative(stack)), goal);
    }

    @Test
    void plainTreesStayOnTheEcoPlanner() {
        var ingot = stack(TestItems.IRON_INGOT);
        var raw = stack(TestItems.RAW_IRON);
        produce(ingot, false, false, raw);
        produce(raw, false, false);
        assertNull(reason(ingot));
    }

    @Test
    void reachableProcessingSubstitutionRoutesToAe2() {
        var ingot = stack(TestItems.IRON_INGOT);
        var raw = stack(TestItems.RAW_IRON);
        produce(ingot, false, false, raw);
        produce(raw, false, true);
        assertEquals("processing pattern with substitution", reason(ingot));
    }

    @Test
    void craftingSubstitutionIsModelledByEco() {
        var brick = stack(TestItems.BRICK);
        produce(brick, true, true);
        assertNull(reason(brick));
    }

    @Test
    void unreachablePatternsDoNotAffectTheRequest() {
        var ingot = stack(TestItems.IRON_INGOT);
        produce(ingot, false, false);
        var coal = stack(TestItems.COAL);
        produce(coal, false, true);
        assertNull(reason(ingot));
    }

    @Test
    void inputOnlyPatternRoutesToAe2() {
        var ingot = stack(TestItems.IRON_INGOT);
        var pattern = produce(ingot, false, false);
        when(pattern.isInputOnly()).thenReturn(true);
        assertEquals("input-only pattern", reason(ingot));
    }

    @Test
    void cyclesTerminate() {
        var a = stack(TestItems.COPPER_INGOT);
        var b = stack(TestItems.RAW_COPPER);
        produce(a, false, false, b);
        produce(b, false, false, a);
        assertNull(reason(a));
    }
}
