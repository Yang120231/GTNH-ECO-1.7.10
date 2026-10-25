package cn.dancingsnow.neoecoae.platform.nbt;

public final class FloatTag extends NumericTag {

    FloatTag(net.minecraft.nbt.NBTTagFloat value) {
        super(value);
    }

    public static FloatTag valueOf(float value) {
        return new FloatTag(new net.minecraft.nbt.NBTTagFloat(value));
    }
}
