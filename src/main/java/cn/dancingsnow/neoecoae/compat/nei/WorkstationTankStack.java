package cn.dancingsnow.neoecoae.compat.nei;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.StackInfo;

final class WorkstationTankStack extends PositionedStack {

    private final List<FluidStack> fluids;

    WorkstationTankStack(List<ItemStack> displays, List<FluidStack> fluids, int x, int y) {
        super(displays, x, y, false);
        this.fluids = fluids;
    }

    FluidStack fluid() {
        var selected = StackInfo.getFluid(item);
        for (var fluid : fluids) if (selected != null && fluid.isFluidEqual(selected)) return fluid;
        return fluids.get(0);
    }

    List<String> tooltip() {
        var fluid = fluid();
        return new ArrayList<>(
            Arrays.asList(
                fluid.getLocalizedName(),
                NumberFormat.getIntegerInstance(Locale.US)
                    .format(fluid.amount) + " mB"));
    }

    @Override
    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= relx && mouseX < relx + 16 && mouseY >= rely && mouseY < rely + 58;
    }
}
