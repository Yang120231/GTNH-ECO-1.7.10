package cn.dancingsnow.neoecoae.platform.ui.workstation;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;

import cn.dancingsnow.neoecoae.platform.fluid.NativeLargeWorkstationHatch;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.ui.NativeFluidSlot;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.NativeOwnedFluidSync;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;

/**
 * Real coolant tanks use the same native inventory artwork and conserved container interaction as other ECO hatches.
 */
public final class NativeLargeWorkstationHatchPages {

    private NativeLargeWorkstationHatchPages() {}

    public static ModularPanel create(NativeLargeWorkstationHatch host, boolean input) {
        ModularPanel panel = ModularPanel
            .defaultPanel("neoecoae_large_" + (input ? "input" : "output") + "_hatch", 170, 136)
            .background(NativeUiStyle.panel());
        panel.child(
            new NativeText(
                () -> Component.translatable(
                    "block.neoecoae." + host.getType()
                        .id()),
                .85f,
                0x3f3d52).pos(4, 4)
                    .size(162, 10));
        panel.child(
            new NativeFluidSlot().syncHandler(new NativeOwnedFluidSync(host, host.tank, input, true))
                .background(NativeUiStyle.inventoryBorder())
                .alwaysShowFull(false)
                .pos(76, 25)
                .size(18));
        NativeUiStyle.inventoryFrames(panel, 4, 55);
        panel.child(
            SlotGroupWidget.playerInventory((index, slot) -> new NativeItemSlot().background(NativeUiStyle.slot()))
                .pos(4, 55));
        return panel;
    }
}
