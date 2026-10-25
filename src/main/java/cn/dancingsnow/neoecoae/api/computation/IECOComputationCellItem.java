package cn.dancingsnow.neoecoae.api.computation;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

/** Computation memory, independent of storage cells and of a particular Item base class. */
public interface IECOComputationCellItem {

    StorageTierDescriptor getTier();

    default StorageTierDescriptor getTier(ItemStack stack) {
        return getTier();
    }

    default long getComputationBytes(ItemStack stack) {
        return getTier(stack).getCPUTotalBytes();
    }
}
