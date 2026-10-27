package cn.dancingsnow.neoecoae.compat.nei;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Loaded only when GregTech is installed; preserves NEI's searchable fluid identity and amount. */
final class GregTechFluidDisplay {

    private GregTechFluidDisplay() {}

    static ItemStack create(FluidStack fluid) {
        return gregtech.api.util.GTUtility.getFluidDisplayStack(fluid, true);
    }
}
