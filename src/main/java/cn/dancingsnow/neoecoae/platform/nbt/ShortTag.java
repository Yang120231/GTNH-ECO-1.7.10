package cn.dancingsnow.neoecoae.platform.nbt;

public final class ShortTag extends NumericTag {

    ShortTag(net.minecraft.nbt.NBTTagShort value) {
        super(value);
    }

    public static ShortTag valueOf(short value) {
        return new ShortTag(new net.minecraft.nbt.NBTTagShort(value));
    }
}
