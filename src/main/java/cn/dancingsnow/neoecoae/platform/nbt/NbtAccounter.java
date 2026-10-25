package cn.dancingsnow.neoecoae.platform.nbt;

import net.minecraft.nbt.NBTSizeTracker;

public final class NbtAccounter {

    final long limit;

    private NbtAccounter(long limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative NBT budget");
        this.limit = limit;
    }

    public static NbtAccounter create(long limit) {
        return new NbtAccounter(limit);
    }

    public static NbtAccounter unlimitedHeap() {
        return new NbtAccounter(Long.MAX_VALUE);
    }

    NBTSizeTracker nativeTracker() {
        return new NBTSizeTracker(limit);
    }
}
