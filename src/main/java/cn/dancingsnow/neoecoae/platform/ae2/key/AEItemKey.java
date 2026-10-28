package cn.dancingsnow.neoecoae.platform.ae2.key;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import appeng.util.item.AESharedNBT;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

public final class AEItemKey extends AEKey {

    // Content hashes of rv3's interned tags; weak identity keys, so lookups never walk NBT.
    private static final java.util.concurrent.ConcurrentMap<NBTTagCompound, Integer> SHARED_TAG_HASHES = new com.google.common.collect.MapMaker()
        .weakKeys()
        .makeMap();

    /** Private, never handed out and never mutated: callers only ever receive copies. */
    private final ItemStack identity;
    private final int hash;
    // Lazily built native view; {@link #toNative} copies it instead of re-interning the tag per call.
    private IAEItemStack nativeTemplate;

    private AEItemKey(ItemStack identity, int tagHash, IAEItemStack nativeTemplate) {
        this.identity = identity;
        this.nativeTemplate = nativeTemplate;
        hash = (31 * identity.getItem()
            .hashCode() + identity.getItemDamage()) * 31 + tagHash;
    }

    public static AEItemKey of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        ItemStack identity = stack.copy();
        identity.stackSize = 1;
        NBTTagCompound tag = identity.getTagCompound();
        return new AEItemKey(identity, tag == null ? 0 : tag.hashCode(), null);
    }

    /** Network ingress: rv3 stacks carry an interned tag, so neither the stack nor its NBT is copied. */
    static AEItemKey ofNative(IAEItemStack stack) {
        Object raw = stack.getTagCompound();
        if (stack.getItem() == null
            || raw != null && !(raw instanceof NBTTagCompound && AESharedNBT.isShared((NBTTagCompound) raw)))
            return of(stack.getItemStack());
        NBTTagCompound tag = (NBTTagCompound) raw;
        ItemStack identity = new ItemStack(stack.getItem(), 1, stack.getItemDamage());
        if (tag != null) identity.setTagCompound(tag);
        return new AEItemKey(
            identity,
            tag == null ? 0 : SHARED_TAG_HASHES.computeIfAbsent(tag, NBTTagCompound::hashCode),
            stack.copy());
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
        // Some items build display names from their NBT; never let them see the shared tag.
        return toStack().getDisplayName();
    }

    @Override
    public IAEItemStack toNative(long amount) {
        IAEItemStack template = nativeTemplate;
        if (template == null) nativeTemplate = template = AEItemStack.create(toStack());
        return template.copy()
            .setStackSize(amount);
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
        if (this == other) return true;
        if (!(other instanceof AEItemKey)) return false;
        AEItemKey key = (AEItemKey) other;
        if (hash != key.hash || identity.getItem() != key.identity.getItem()
            || identity.getItemDamage() != key.identity.getItemDamage()) return false;
        NBTTagCompound tag = identity.getTagCompound(), otherTag = key.identity.getTagCompound();
        return tag == otherTag || tag != null && tag.equals(otherTag);
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
