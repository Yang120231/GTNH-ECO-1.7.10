package cn.dancingsnow.neoecoae.platform.sync;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Native inventory/energy values restore into their existing instance, retaining registered callbacks. */
public interface ManagedValue {

    CompoundTag writeManaged();

    void readManaged(CompoundTag tag);
}
