package cn.dancingsnow.neoecoae.platform.ui.storage;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

/** Native boundary for StorageResourceFilterGrid.sample: sampling never drains or moves the real item. */
public final class NativeStorageFilters {

    private NativeStorageFilters() {}

    public static AEKey sample(ItemStack stack, boolean asItem) {
        if (ItemStacks.isEmpty(stack)) return null;
        GenericStack generic = GenericStack.fromItemStack(stack);
        if (generic != null && generic.what() instanceof AEFluidKey) return generic.what();
        if (!asItem) {
            ItemStack copy = stack.copy();
            FluidStack fluid = copy.getItem() instanceof IFluidContainerItem
                ? ((IFluidContainerItem) copy.getItem()).getFluid(copy)
                : FluidContainerRegistry.getFluidForFilledItem(copy);
            if (fluid != null && fluid.amount > 0) return AEFluidKey.of(fluid);
        }
        return AEItemKey.of(stack);
    }
}
