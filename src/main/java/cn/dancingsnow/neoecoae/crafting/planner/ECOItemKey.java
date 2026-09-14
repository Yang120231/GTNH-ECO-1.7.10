package cn.dancingsnow.neoecoae.crafting.planner;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Version-neutral identity used by the planner; deliberately avoids modern AE2 stack APIs. */
public final class ECOItemKey {

    private final int itemId;
    private final int damage;
    private final NBTTagCompound tag;

    private ECOItemKey(int itemId, int damage, NBTTagCompound tag) {
        this.itemId = itemId;
        this.damage = damage;
        this.tag = tag == null ? null : (NBTTagCompound) tag.copy();
    }

    public static ECOItemKey of(ItemStack stack) {
        return stack == null || stack.getItem() == null ? null
            : new ECOItemKey(
                net.minecraft.item.Item.getIdFromItem(stack.getItem()),
                stack.getItemDamage(),
                stack.getTagCompound());
    }

    public int itemId() {
        return itemId;
    }

    public int damage() {
        return damage;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ECOItemKey)) return false;
        ECOItemKey k = (ECOItemKey) o;
        return itemId == k.itemId && damage == k.damage && java.util.Objects.equals(tag, k.tag);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * itemId + damage) + (tag == null ? 0 : tag.hashCode());
    }

    @Override
    public String toString() {
        return itemId + ":" + damage;
    }
}
