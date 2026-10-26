package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

import cn.dancingsnow.neoecoae.platform.fluid.FluidTank;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.ui.NativeOwnedFluidSync;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;

/** Direct expansion of fluid_hatch.json: padding 4, gap 2 and the slot's two 10px margins. */
public final class NativeFluidHatchPages {
    private NativeFluidHatchPages() {}

    public static ModularPanel create(TileEntity host, FluidTank tank, boolean input) {
        var panel = ModularPanel.defaultPanel("neoecoae_" + (input ? "input" : "output") + "_hatch", 170, 136)
            .background(NativeUiStyle.panel());
        panel.child(new NativeText(() -> Component.translatable("block.neoecoae." + (input ? "input" : "output") + "_hatch"),
            1, 0x3f3d52).pos(4, 4).size(162, 9));
        panel.child(new FluidSlot().syncHandler(new NativeOwnedFluidSync(host, tank.nativeTank(), input, true))
            .background(NativeUiStyle.inventoryBorder()).alwaysShowFull(false).pos(76, 25).size(18));
        NativeUiStyle.inventoryFrames(panel, 4, 55);
        panel.child(SlotGroupWidget.playerInventory((index, slot) -> slot.background(NativeUiStyle.slot()))
            .pos(4, 55));
        return panel;
    }
}
