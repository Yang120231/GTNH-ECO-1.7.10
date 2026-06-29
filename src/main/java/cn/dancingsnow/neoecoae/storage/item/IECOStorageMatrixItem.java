package cn.dancingsnow.neoecoae.storage.item;

import net.minecraft.item.ItemStack;

import appeng.api.storage.StorageChannel;

public interface IECOStorageMatrixItem {

    StorageChannel getStorageChannel(ItemStack stack);

    long getDisplayBytes(ItemStack stack);
}
