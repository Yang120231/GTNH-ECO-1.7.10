package cn.dancingsnow.neoecoae.platform.nbt;

public final class DoubleTag extends NumericTag {

    DoubleTag(net.minecraft.nbt.NBTTagDouble value) {
        super(value);
    }

    public static DoubleTag valueOf(double value) {
        return new DoubleTag(new net.minecraft.nbt.NBTTagDouble(value));
    }
}
