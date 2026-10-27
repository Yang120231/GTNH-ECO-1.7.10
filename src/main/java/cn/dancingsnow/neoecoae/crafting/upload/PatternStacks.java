package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

/** Item identity used by upload: item, meta and the complete NBT. Quantities are compared separately. */
public final class PatternStacks {

    private PatternStacks() {}

    public static boolean sameItem(ItemStack left, ItemStack right) {
        return left != null && right != null
            && left.getItem() == right.getItem()
            && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    public static boolean sameStack(ItemStack left, ItemStack right) {
        if (left == null || right == null) return left == right;
        return left.stackSize == right.stackSize && sameItem(left, right);
    }

    public static ItemStack copy(ItemStack stack, int count) {
        if (stack == null || count <= 0) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = count;
        return copy;
    }

    public static int count(ItemStack stack) {
        return stack == null ? 0 : Math.max(0, stack.stackSize);
    }
}
