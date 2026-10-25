package cn.dancingsnow.neoecoae.platform.nbt;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Namespaced facade over real 1.7.10 tags. Unknown tag values round-trip without conversion. */
public class Tag {

    public static final int TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3, TAG_LONG = 4, TAG_FLOAT = 5,
        TAG_DOUBLE = 6, TAG_BYTE_ARRAY = 7, TAG_STRING = 8, TAG_LIST = 9, TAG_COMPOUND = 10, TAG_INT_ARRAY = 11,
        TAG_ANY_NUMERIC = 99;
    protected final NBTBase value;

    protected Tag(NBTBase value) {
        this.value = java.util.Objects.requireNonNull(value);
    }

    public NBTBase nativeTag() {
        return value;
    }

    public int getId() {
        return value.getId();
    }

    public String getAsString() {
        return value instanceof net.minecraft.nbt.NBTTagString
            ? ((net.minecraft.nbt.NBTTagString) value).func_150285_a_()
            : value.toString();
    }

    public Tag copy() {
        return wrap(value.copy());
    }

    public static Tag wrap(NBTBase value) {
        if (value == null) return null;
        if (value instanceof net.minecraft.nbt.NBTTagByte) return new ByteTag((net.minecraft.nbt.NBTTagByte) value);
        if (value instanceof net.minecraft.nbt.NBTTagShort) return new ShortTag((net.minecraft.nbt.NBTTagShort) value);
        if (value instanceof net.minecraft.nbt.NBTTagInt) return new IntTag((net.minecraft.nbt.NBTTagInt) value);
        if (value instanceof net.minecraft.nbt.NBTTagLong) return new LongTag((net.minecraft.nbt.NBTTagLong) value);
        if (value instanceof net.minecraft.nbt.NBTTagFloat) return new FloatTag((net.minecraft.nbt.NBTTagFloat) value);
        if (value instanceof net.minecraft.nbt.NBTTagDouble)
            return new DoubleTag((net.minecraft.nbt.NBTTagDouble) value);
        if (value instanceof NBTTagCompound) return new CompoundTag((NBTTagCompound) value);
        if (value instanceof NBTTagList) return new ListTag((NBTTagList) value);
        return new Tag(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Tag && value.equals(((Tag) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
