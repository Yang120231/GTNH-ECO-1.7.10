package cn.dancingsnow.neoecoae.platform.nbt;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

public final class CompoundTag extends Tag {

    public void putDouble(String key, double value) {
        nativeTag().setDouble(key, value);
    }

    public double getDouble(String key) {
        return nativeTag().getDouble(key);
    }

    public CompoundTag() {
        this(new NBTTagCompound());
    }

    public CompoundTag(NBTTagCompound tag) {
        super(tag);
    }

    @Override
    public NBTTagCompound nativeTag() {
        return (NBTTagCompound) value;
    }

    @Override
    public CompoundTag copy() {
        return new CompoundTag((NBTTagCompound) value.copy());
    }

    public boolean isEmpty() {
        return nativeTag().hasNoTags();
    }

    public java.util.Set<String> getAllKeys() {
        return java.util.Collections.unmodifiableSet(nativeTag().func_150296_c());
    }

    public boolean contains(String key) {
        return nativeTag().hasKey(key);
    }

    public boolean contains(String key, int type) {
        return nativeTag().hasKey(key, type);
    }

    public Tag get(String key) {
        return Tag.wrap(nativeTag().getTag(key));
    }

    public void put(String key, Tag tag) {
        nativeTag().setTag(key, tag.nativeTag());
    }

    public void remove(String key) {
        nativeTag().removeTag(key);
    }

    public CompoundTag getCompound(String key) {
        return new CompoundTag(nativeTag().getCompoundTag(key));
    }

    public ListTag getList(String key, int type) {
        return new ListTag(nativeTag().getTagList(key, type));
    }

    public String getString(String key) {
        return nativeTag().getString(key);
    }

    public void putString(String key, String item) {
        nativeTag().setString(key, item);
    }

    public int getInt(String key) {
        return nativeTag().getInteger(key);
    }

    public void putInt(String key, int item) {
        nativeTag().setInteger(key, item);
    }

    public long getLong(String key) {
        return nativeTag().getLong(key);
    }

    public void putLong(String key, long item) {
        nativeTag().setLong(key, item);
    }

    public boolean getBoolean(String key) {
        return nativeTag().getBoolean(key);
    }

    public void putBoolean(String key, boolean item) {
        nativeTag().setBoolean(key, item);
    }

    public byte[] getByteArray(String key) {
        return nativeTag().getByteArray(key);
    }

    public void putByteArray(String key, byte[] item) {
        nativeTag().setByteArray(key, item);
    }

    public void putIntArray(String key, int[] item) {
        nativeTag().setIntArray(key, item);
    }

    public int[] getIntArray(String key) {
        return nativeTag().getIntArray(key);
    }

    /** Legacy NBT has no long-array tag; use a homogeneous native list of exact signed longs. */
    public void putLongArray(String key, long[] values) {
        ListTag list = new ListTag();
        for (long value : values) list.add(LongTag.valueOf(value));
        put(key, list);
    }

    public long[] getLongArray(String key) {
        ListTag list = getList(key, TAG_LONG);
        long[] values = new long[list.size()];
        for (int i = 0; i < values.length; i++) values[i] = ((NumericTag) list.get(i)).getAsLong();
        return values;
    }

    public boolean hasUUID(String key) {
        return contains(key, TAG_INT_ARRAY) && nativeTag().getIntArray(key).length == 4;
    }

    public UUID getUUID(String key) {
        return NbtUtils.loadUUID(get(key));
    }

    public void putUUID(String key, UUID id) {
        put(key, NbtUtils.createUUID(id));
    }
}
