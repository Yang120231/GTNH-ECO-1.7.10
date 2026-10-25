package cn.dancingsnow.neoecoae.platform.nbt;

/** Copy-on-write custom-data component, stored separately from native workbench metadata. */
public final class CustomData {

    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag.copy());
    }

    static CustomData view(CompoundTag tag) {
        return new CustomData(tag);
    }

    public CompoundTag copyTag() {
        return tag.copy();
    }

    /** Only read-only business probes call this method. */
    public CompoundTag getUnsafe() {
        return tag;
    }
}
