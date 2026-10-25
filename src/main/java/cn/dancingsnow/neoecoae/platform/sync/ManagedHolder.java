package cn.dancingsnow.neoecoae.platform.sync;

import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

public interface ManagedHolder {

    default String getSyncTag() {
        return "neoecoae_sync";
    }

    default CompoundTag serializeInitialData(HolderLookup.Provider registries) {
        return ManagedFields.write(this, true);
    }
}
