package cn.dancingsnow.neoecoae.platform.nbt;

import java.util.UUID;

import net.minecraft.nbt.NBTTagIntArray;

public final class NbtUtils {

    private NbtUtils() {}

    public static Tag createUUID(UUID id) {
        long high = id.getMostSignificantBits(), low = id.getLeastSignificantBits();
        return Tag
            .wrap(new NBTTagIntArray(new int[] { (int) (high >>> 32), (int) high, (int) (low >>> 32), (int) low }));
    }

    public static UUID loadUUID(Tag tag) {
        if (tag == null || !(tag.nativeTag() instanceof NBTTagIntArray))
            throw new IllegalArgumentException("UUID must be an int array");
        int[] parts = ((NBTTagIntArray) tag.nativeTag()).func_150302_c();
        if (parts.length != 4) throw new IllegalArgumentException("Invalid UUID length");
        return new UUID(
            ((long) parts[0] << 32) | (parts[1] & 0xffffffffL),
            ((long) parts[2] << 32) | (parts[3] & 0xffffffffL));
    }

    public static void addCurrentDataVersion(CompoundTag root) {
        root.putInt("neoecoaePlatformVersion", 1);
    }
}
