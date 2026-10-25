package cn.dancingsnow.neoecoae.platform.nbt;

/** Numeric access delegates to the real NBT primitive; values keep their original tag width. */
public abstract class NumericTag extends Tag {

    protected NumericTag(net.minecraft.nbt.NBTBase value) {
        super(value);
    }

    public long getAsLong() {
        return ((net.minecraft.nbt.NBTBase.NBTPrimitive) value).func_150291_c();
    }

    public double getAsDouble() {
        return ((net.minecraft.nbt.NBTBase.NBTPrimitive) value).func_150286_g();
    }

    public int getAsInt() {
        return ((net.minecraft.nbt.NBTBase.NBTPrimitive) value).func_150287_d();
    }
}
