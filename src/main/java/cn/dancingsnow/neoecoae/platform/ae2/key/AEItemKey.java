package cn.dancingsnow.neoecoae.platform.ae2.key;

import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

public final class AEItemKey extends AEKey {

    private final ItemStack identity;
    private final int hash;

    private AEItemKey(ItemStack stack) {
        identity = stack.copy();
        identity.stackSize = 1;
        hash = Objects.hash(identity.getItem(), identity.getItemDamage(), identity.getTagCompound());
    }

    public static AEItemKey of(ItemStack stack) {
        return stack == null || stack.getItem() == null ? null : new AEItemKey(stack);
    }

    public static AEItemKey of(Item item) {
        return item == null ? null : of(new ItemStack(item));
    }

    public Item getItem() {
        return identity.getItem();
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        return toTagGeneric(registries);
    }

    public static AEItemKey fromTag(HolderLookup.Provider registries, CompoundTag tag) {
        AEKey key = AEKey.fromTagGeneric(registries, tag);
        return key instanceof AEItemKey ? (AEItemKey) key : null;
    }

    @Override
    public cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation getId() {
        return cn.dancingsnow.neoecoae.platform.registry.BuiltInRegistries.ITEM.getKey(getItem());
    }

    public boolean isDamaged() {
        return identity.isItemDamaged();
    }

    public ItemStack toStack() {
        return identity.copy();
    }

    /** A copy is necessary on the mutable 1.7.10 stack API. */
    public ItemStack getReadOnlyStack() {
        return toStack();
    }

    public ItemStack toStack(int count) {
        ItemStack stack = toStack();
        stack.stackSize = count;
        return stack;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyType.items();
    }

    @Override
    public Object getPrimaryKey() {
        return getItem();
    }

    @Override
    public int getFuzzySearchValue() {
        return identity.getItemDamage();
    }

    @Override
    public int getFuzzySearchMaxValue() {
        return identity.getMaxDamage();
    }

    @Override
    public String getDisplayName() {
        return identity.getDisplayName();
    }

    @Override
    public IAEItemStack toNative(long amount) {
        IAEItemStack stack = AEItemStack.create(toStack());
        return stack == null ? null : stack.setStackSize(amount);
    }

    @Override
    public CompoundTag toTagGeneric(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(
            "#type",
            getType().getId()
                .toString());
        cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation registered = registries.itemKey(getItem());
        String id = registered == null ? null : registered.toString();
        if (id == null) throw new IllegalStateException(
            "Cannot persist an unregistered native item: " + getItem().getClass()
                .getName());
        tag.putString("id", id);
        tag.putInt("damage", identity.getItemDamage());
        if (identity.hasTagCompound()) tag.put(
            "tag",
            new CompoundTag(
                (NBTTagCompound) identity.getTagCompound()
                    .copy()));
        return tag;
    }

    static AEItemKey read(HolderLookup.Provider registries, CompoundTag tag) {
        Object item = registries
            .item(cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse(tag.getString("id")));
        if (!(item instanceof Item)) return null;
        ItemStack stack = new ItemStack((Item) item, 1, tag.getInt("damage"));
        if (tag.contains("tag", 10)) stack.setTagCompound(
            tag.getCompound("tag")
                .copy()
                .nativeTag());
        return of(stack);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AEItemKey && identity.getItem() == ((AEItemKey) other).identity.getItem()
            && identity.getItemDamage() == ((AEItemKey) other).identity.getItemDamage()
            && Objects.equals(identity.getTagCompound(), ((AEItemKey) other).identity.getTagCompound());
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        String id = (String) Item.itemRegistry.getNameForObject(getItem());
        return (id == null ? getItem().getClass()
            .getName() + "@" + Integer.toHexString(System.identityHashCode(getItem())) : id) + ":"
            + identity.getItemDamage()
            + ":"
            + String.valueOf(identity.getTagCompound());
    }
}
