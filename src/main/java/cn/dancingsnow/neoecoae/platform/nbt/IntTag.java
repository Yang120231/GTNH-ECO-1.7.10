package cn.dancingsnow.neoecoae.platform.nbt;

public final class IntTag extends NumericTag {

    IntTag(net.minecraft.nbt.NBTTagInt value) {
        super(value);
    }

    public static IntTag valueOf(int value) {
        return new IntTag(new net.minecraft.nbt.NBTTagInt(value));
    }
}
