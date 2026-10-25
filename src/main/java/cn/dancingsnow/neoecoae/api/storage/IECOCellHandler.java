package cn.dancingsnow.neoecoae.api.storage;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;

public interface IECOCellHandler {

    boolean isCell(ItemStack stack);

    @Nullable
    IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host);

    default void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {}

    default void clearRuntimeState() {}
}
