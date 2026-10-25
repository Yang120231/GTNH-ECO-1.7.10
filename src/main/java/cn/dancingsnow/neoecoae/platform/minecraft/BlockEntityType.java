package cn.dancingsnow.neoecoae.platform.minecraft;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.tileentity.TileEntity;

/** Stable registration key and native no-argument tile factory. */
public final class BlockEntityType<T extends TileEntity> {

    private final String id;
    private final Supplier<T> factory;

    public BlockEntityType(String id, Supplier<T> factory) {
        this.id = Objects.requireNonNull(id);
        this.factory = Objects.requireNonNull(factory);
    }

    public String id() {
        return id;
    }

    public T create() {
        return Objects.requireNonNull(factory.get(), "Tile factory returned null: " + id);
    }
}
