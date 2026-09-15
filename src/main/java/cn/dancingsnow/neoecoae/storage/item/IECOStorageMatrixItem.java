package cn.dancingsnow.neoecoae.storage.item;

import net.minecraft.item.ItemStack;

public interface IECOStorageMatrixItem {

    default String getStorageChannel() {
        return "item";
    }

    long getDisplayBytes(ItemStack stack);

    long getBytesPerType(ItemStack stack);
}
