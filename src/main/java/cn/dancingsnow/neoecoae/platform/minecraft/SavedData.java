package cn.dancingsnow.neoecoae.platform.minecraft;

import java.io.File;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Domain serialization contract; the native world adapter owns lifecycle and file placement. */
public abstract class SavedData {

    private boolean dirty;

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty() {
        dirty = true;
    }

    public void setDirty(boolean value) {
        dirty = value;
    }

    public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);

    public abstract void save(File file, HolderLookup.Provider registries);

    @com.github.bsideup.jabel.Desugar
    public record Factory<T extends SavedData> (Supplier<T> constructor,
        BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer) {}
}
