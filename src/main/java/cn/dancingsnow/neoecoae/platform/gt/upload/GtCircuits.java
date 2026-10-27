package cn.dancingsnow.neoecoae.platform.gt.upload;

import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.compat.proghatches.upload.ProgrammableHatchUpload;
import gregtech.common.items.ItemIntegratedCircuit;

/** GT integrated circuits and their Programmable Hatches wrapper, normalized to one comparable form. */
final class GtCircuits {

    private GtCircuits() {}

    static boolean isCircuit(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemIntegratedCircuit)) return false;
        int damage = stack.getItemDamage();
        return damage >= 0 && damage <= ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER;
    }

    /** The comparable circuit for an integrated or programming circuit; null for anything else. */
    static ItemStack unwrap(ItemStack stack) {
        if (isCircuit(stack)) return single(stack);
        ItemStack wrapped = ProgrammableHatchUpload.unwrap(stack);
        return isCircuit(wrapped) ? single(wrapped) : null;
    }

    /** Recipe form of an ingredient; an empty programming circuit becomes null (a reset marker). */
    static ItemStack normalizeForRecipe(ItemStack stack) {
        if (stack == null) return null;
        if (ProgrammableHatchUpload.isProgrammingCircuit(stack)) return unwrap(stack);
        return stack.copy();
    }

    static boolean same(ItemStack left, ItemStack right) {
        ItemStack a = unwrap(left), b = unwrap(right);
        return a != null && b != null && (a.getItemDamage() & 0xFF) == (b.getItemDamage() & 0xFF);
    }

    static String number(ItemStack circuit) {
        ItemStack value = unwrap(circuit);
        return value == null ? "-" : Integer.toString(value.getItemDamage() & 0xFF);
    }

    /** The selector encoded in a processing pattern, if any. */
    static ItemStack patternCircuit(ICraftingPatternDetails details) {
        if (details == null || details.isCraftable() || details.getCondensedAEInputs() == null) return null;
        for (IAEStack<?> input : details.getCondensedAEInputs()) {
            if (!(input instanceof IAEItemStack)) continue;
            ItemStack circuit = unwrap(((IAEItemStack) input).getItemStack());
            if (circuit != null) return circuit;
        }
        return null;
    }

    static boolean hasProgrammingCircuit(ICraftingPatternDetails details) {
        if (details == null || details.getCondensedAEInputs() == null) return false;
        for (IAEStack<?> input : details.getCondensedAEInputs()) if (input instanceof IAEItemStack
            && ProgrammableHatchUpload.isProgrammingCircuit(((IAEItemStack) input).getItemStack())) return true;
        return false;
    }

    private static ItemStack single(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }
}
