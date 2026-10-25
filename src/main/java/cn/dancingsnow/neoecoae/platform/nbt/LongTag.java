package cn.dancingsnow.neoecoae.platform.nbt;

public final class LongTag extends NumericTag {

    LongTag(net.minecraft.nbt.NBTTagLong value) {
        super(value);
    }

    public static LongTag valueOf(long value) {
        return new LongTag(new net.minecraft.nbt.NBTTagLong(value));
    }
}
