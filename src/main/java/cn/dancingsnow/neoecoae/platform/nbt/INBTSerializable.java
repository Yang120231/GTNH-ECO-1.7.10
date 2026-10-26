package cn.dancingsnow.neoecoae.platform.nbt;

import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;

/** Explicit registry context for source serialization, backed by native Minecraft NBT. */
public interface INBTSerializable<T extends Tag> {

    T serializeNBT(HolderLookup.Provider registries);

    void deserializeNBT(HolderLookup.Provider registries, T tag);
}
