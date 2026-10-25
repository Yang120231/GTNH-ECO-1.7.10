package cn.dancingsnow.neoecoae.platform.minecraft;

import net.minecraft.item.ItemStack;

public final class ItemStacks {

    private ItemStacks() {}

    @SafeVarargs
    public static java.util.List<ItemStack> listOf(ItemStack... stacks) {
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(java.util.Arrays.asList(stacks)));
    }

    public static ItemStack copy(ItemStack stack) {
        return isEmpty(stack) ? null : stack.copy();
    }

    public static cn.dancingsnow.neoecoae.platform.nbt.CompoundTag save(HolderLookup.Provider registries,
        ItemStack stack) {
        var tag = new cn.dancingsnow.neoecoae.platform.nbt.CompoundTag();
        if (isEmpty(stack)) return tag;
        ResourceLocation id = registries.itemKey(stack.getItem());
        if (id == null) throw new IllegalArgumentException("Unregistered item in UI snapshot");
        tag.putString("id", id.toString());
        tag.putInt("count", stack.stackSize);
        tag.putInt("damage", stack.getItemDamage());
        if (stack.hasTagCompound()) tag.put(
            "tag",
            new cn.dancingsnow.neoecoae.platform.nbt.CompoundTag(
                (net.minecraft.nbt.NBTTagCompound) stack.getTagCompound()
                    .copy()));
        return tag;
    }

    public static ItemStack parseOptional(HolderLookup.Provider registries,
        cn.dancingsnow.neoecoae.platform.nbt.CompoundTag tag) {
        if (!tag.contains("id", 8) || tag.getInt("count") <= 0) return null;
        net.minecraft.item.Item item = registries.item(ResourceLocation.parse(tag.getString("id")));
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, tag.getInt("count"), tag.getInt("damage"));
        if (tag.contains("tag", 10)) stack.setTagCompound(
            tag.getCompound("tag")
                .copy()
                .nativeTag());
        return stack;
    }

    public static int count(ItemStack stack) {
        return isEmpty(stack) ? 0 : stack.stackSize;
    }

    public static ItemStack copyWithCount(ItemStack stack, int count) {
        if (isEmpty(stack) || count <= 0) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = count;
        return copy;
    }

    public static void shrink(ItemStack stack, int count) {
        if (stack != null) stack.stackSize = Math.max(0, stack.stackSize - count);
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.stackSize <= 0 || stack.getItem() == null;
    }

    public static boolean isComponentsPatchEmpty(ItemStack stack) {
        return isEmpty(stack) || (!stack.hasTagCompound() && stack.getItemDamage() == 0);
    }

    public static boolean isSameItem(ItemStack left, ItemStack right) {
        return !isEmpty(left) && !isEmpty(right) && left.getItem() == right.getItem();
    }

    public static boolean isSameItemSameComponents(ItemStack left, ItemStack right) {
        return isSameItem(left, right) && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }
}
