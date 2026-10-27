package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Fluid slots use the same foreground outline as item slots in the modern ECO theme. */
public class NativeFluidSlot extends FluidSlot {

    @Override
    @SideOnly(Side.CLIENT)
    public int getSlotHoverColor() {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawForeground(ModularGuiContext context) {
        if (isHovering()) NativeSlotHighlight.draw(1, 1, getArea().width - 2, getArea().height - 2);
        super.drawForeground(context);
    }
}
