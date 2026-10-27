package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Real container slots keep native interactions and use the shared ECO hover outline. */
public class NativeItemSlot extends ItemSlot {

    @Override
    @SideOnly(Side.CLIENT)
    protected void drawOverlay() {}

    @Override
    @SideOnly(Side.CLIENT)
    public void drawForeground(ModularGuiContext context) {
        if (isHovering()) NativeSlotHighlight.draw(1, 1, 16, 16);
        super.drawForeground(context);
    }
}
