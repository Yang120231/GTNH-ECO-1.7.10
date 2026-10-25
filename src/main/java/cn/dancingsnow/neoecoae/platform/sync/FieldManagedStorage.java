package cn.dancingsnow.neoecoae.platform.sync;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Per-owner field access; annotation codecs are shared by native NBT and description packets. */
public final class FieldManagedStorage {

    private final Object owner;

    public FieldManagedStorage(Object owner) {
        this.owner = java.util.Objects.requireNonNull(owner);
    }

    public CompoundTag serialize(boolean description) {
        return ManagedFields.write(owner, description);
    }

    public void deserialize(CompoundTag values, boolean description) {
        ManagedFields.read(owner, values, description);
    }
}
