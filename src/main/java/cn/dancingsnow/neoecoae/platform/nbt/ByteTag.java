package cn.dancingsnow.neoecoae.platform.nbt;

public final class ByteTag extends NumericTag {

    ByteTag(net.minecraft.nbt.NBTTagByte value) {
        super(value);
    }

    public static ByteTag valueOf(byte value) {
        return new ByteTag(new net.minecraft.nbt.NBTTagByte(value));
    }
}
