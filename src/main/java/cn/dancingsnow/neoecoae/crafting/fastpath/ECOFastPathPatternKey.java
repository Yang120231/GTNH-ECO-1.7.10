package cn.dancingsnow.neoecoae.crafting.fastpath;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public final class ECOFastPathPatternKey {

    private final int itemId;
    private final int itemDamage;
    private final int stackSize;
    private final NBTTagCompound tag;

    private ECOFastPathPatternKey(int itemId, int itemDamage, int stackSize, NBTTagCompound tag) {
        this.itemId = itemId;
        this.itemDamage = itemDamage;
        this.stackSize = stackSize;
        this.tag = tag == null ? null : (NBTTagCompound) tag.copy();
    }

    public static ECOFastPathPatternKey of(ICraftingPatternDetails patternDetails) {
        if (patternDetails == null) {
            return null;
        }
        try {
            return of(patternDetails.getPattern());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static ECOFastPathPatternKey of(ItemStack patternStack) {
        if (patternStack == null || patternStack.getItem() == null) {
            return null;
        }
        NBTTagCompound tag = patternStack.getTagCompound();
        return new ECOFastPathPatternKey(
            ItemStackId.itemId(patternStack),
            patternStack.getItemDamage(),
            Math.max(0, patternStack.stackSize),
            tag);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ECOFastPathPatternKey)) {
            return false;
        }
        ECOFastPathPatternKey that = (ECOFastPathPatternKey) other;
        return this.itemId == that.itemId && this.itemDamage == that.itemDamage
            && this.stackSize == that.stackSize
            && java.util.Objects.equals(this.tag, that.tag);
    }

    @Override
    public int hashCode() {
        int result = this.itemId;
        result = 31 * result + this.itemDamage;
        result = 31 * result + this.stackSize;
        result = 31 * result + (this.tag == null ? 0 : this.tag.hashCode());
        return result;
    }

    private static final class ItemStackId {

        private ItemStackId() {}

        private static int itemId(ItemStack stack) {
            return stack.getItem() == null ? 0 : net.minecraft.item.Item.getIdFromItem(stack.getItem());
        }
    }
}
