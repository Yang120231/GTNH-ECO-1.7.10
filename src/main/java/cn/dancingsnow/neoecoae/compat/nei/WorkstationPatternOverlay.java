package cn.dancingsnow.neoecoae.compat.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.glodblock.github.common.item.ItemFluidPacket;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;

/** Uses the terminal's native typed slot synchronization, also for AE2FC and wireless subclasses. */
final class WorkstationPatternOverlay implements IOverlayHandler {

    static final WorkstationPatternOverlay INSTANCE = new WorkstationPatternOverlay();

    private WorkstationPatternOverlay() {}

    static IAEStack<?> stack(ItemStack stack, boolean fluid) {
        IAEStack<?> value = fluid ? AEFluidStack.create(ItemFluidPacket.getFluidStack(stack))
            : AEItemStack.create(stack.copy());
        if (value == null) throw new IllegalStateException("Invalid workstation pattern ingredient");
        return value;
    }

    @Override
    public void overlayRecipe(GuiContainer gui, IRecipeHandler recipe, int index, boolean shift) {
        transferRecipe(gui, recipe, index, 1);
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler recipe, int index, int multiplier) {
        if (!(gui.inventorySlots instanceof ContainerPatternTerm terminal)
            || !(recipe instanceof WorkstationRecipeHandler handler)) return 0;
        transfer(terminal, handler.patternStacks(index, false), handler.patternStacks(index, true), multiplier);
        return 0;
    }

    static boolean transfer(ContainerPatternTerm terminal, List<IAEStack<?>> inputs, List<IAEStack<?>> outputs,
        int multiplier) {
        if (inputs.isEmpty() || outputs.isEmpty()
            || inputs.size() > terminal.inputsSync.get()
                .getSizeInventory()
            || outputs.size() > terminal.outputsSync.get()
                .getSizeInventory())
            return false;
        List<IAEStack<?>> scaledInputs = scale(inputs, multiplier);
        List<IAEStack<?>> scaledOutputs = scale(outputs, multiplier);

        // The input sync handler is registered before the mode handler. Send processing mode first, or the
        // server's crafting-mode input callback would discard fluids and reduce item amounts to one.
        terminal.craftingModeSync.set(false);
        terminal.getSyncManager()
            .flushSync();
        terminal.clear();
        for (int i = 0; i < scaledInputs.size(); i++)
            terminal.updateVirtualSlot(StorageName.CRAFTING_INPUT, i, scaledInputs.get(i));
        for (int i = 0; i < scaledOutputs.size(); i++)
            terminal.updateVirtualSlot(StorageName.CRAFTING_OUTPUT, i, scaledOutputs.get(i));
        terminal.getSyncManager()
            .flushSync();
        return true;
    }

    private static List<IAEStack<?>> scale(List<IAEStack<?>> stacks, int multiplier) {
        List<IAEStack<?>> result = new ArrayList<>();
        for (IAEStack<?> stack : stacks) result.add(
            stack.copy()
                .setStackSize(Math.multiplyExact(stack.getStackSize(), Math.max(1, multiplier))));
        return result;
    }
}
