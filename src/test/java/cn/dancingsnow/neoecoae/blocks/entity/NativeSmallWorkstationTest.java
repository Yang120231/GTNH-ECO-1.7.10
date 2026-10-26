package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceIngredient;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;

class NativeSmallWorkstationTest {

    private static ECOIntegratedWorkingStationBlockEntity machine() {
        InventoryTestBootstrap.initialize();
        return new ECOIntegratedWorkingStationBlockEntity(
            new BlockEntityType<>("test", () -> null),
            BlockPos.ZERO,
            new BlockState(mock(Block.class), Collections.emptyMap()));
    }

    @Test
    void blockedItemOutputKeepsAllOwnedInputs() {
        var machine = machine();
        var recipe = new IntegratedWorkingStationRecipe(
            Collections.singletonList(new SizedIngredient(SourceIngredient.items(TestItems.IRON_INGOT), 2)),
            new SizedFluidIngredient(SourceFluidIngredient.empty(), 1),
            new ItemStack(TestItems.DIAMOND, 4),
            null,
            1000);
        machine.getInputInv()
            .setStackInSlot(0, new ItemStack(TestItems.IRON_INGOT, 2));
        machine.getOutputInv()
            .setStackInSlot(0, new ItemStack(TestItems.IRON_INGOT));
        machine.finishTask(recipe);
        assertEquals(
            2,
            machine.getInputInv()
                .getStackInSlot(0).stackSize);
        machine.getOutputInv()
            .setStackInSlot(0, null);
        machine.finishTask(recipe);
        assertNull(
            machine.getInputInv()
                .getStackInSlot(0));
        assertEquals(
            0,
            machine.getInputTank()
                .getFluidAmount());
        assertEquals(
            4,
            machine.getOutputInv()
                .getStackInSlot(0).stackSize);
        machine.finishTask(recipe);
        assertEquals(
            4,
            machine.getOutputInv()
                .getStackInSlot(0).stackSize);
    }

    @Test
    void nativeNbtRetainsPowerProgressAndOutputSettings() {
        var first = machine();
        first.injectAEPower(12345, Actionable.MODULATE);
        first.setProcessingTime(63);
        first.setAutoExport(true);
        first.toggleOutputSide(cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide.LEFT);
        CompoundTag data = new CompoundTag();
        first.saveAdditional(data, RegistryAccess.EMPTY);
        var second = machine();
        second.loadTag(
            new CompoundTag(
                (net.minecraft.nbt.NBTTagCompound) data.nativeTag()
                    .copy()),
            RegistryAccess.EMPTY);
        assertEquals(12345, second.getAECurrentPower());
        assertEquals(63, second.getProcessingTime());
        assertTrue(second.isAutoExport());
        assertFalse(second.isOutputAllowed(cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide.LEFT));
    }

    @Test
    void nativeAutomationCannotExtractInputsOrInsertIntoOutput() {
        var machine = machine();
        assertEquals(10, machine.getSizeInventory());
        assertTrue(machine.canInsertItem(0, new ItemStack(TestItems.IRON_INGOT), 1));
        assertFalse(machine.canInsertItem(9, new ItemStack(TestItems.IRON_INGOT), 1));
        assertFalse(machine.canExtractItem(0, new ItemStack(TestItems.IRON_INGOT), 1));
        assertTrue(machine.canExtractItem(9, new ItemStack(TestItems.IRON_INGOT), 1));
        assertNull(machine.drain(ForgeDirection.DOWN, 1000, true));
    }
}
