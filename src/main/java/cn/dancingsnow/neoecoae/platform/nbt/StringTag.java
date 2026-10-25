package cn.dancingsnow.neoecoae.platform.nbt;

public final class StringTag extends Tag {

    private StringTag(String text) {
        super(new net.minecraft.nbt.NBTTagString(text));
    }

    public static StringTag valueOf(String value) {
        return new StringTag(value);
    }
}
