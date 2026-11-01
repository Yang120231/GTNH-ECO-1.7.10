package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.GlStateManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The modern ECO AE2 outline, drawn in the foreground so adjacent slots cannot cover its edges. */
public final class NativeSlotHighlight {

    private NativeSlotHighlight() {}

    @SideOnly(Side.CLIENT)
    public static void draw(ModularGuiContext context, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        // MUI's foreground traversal updates the context matrix without applying it to OpenGL.
        GlStateManager.pushMatrix();
        try {
            context.applyToOpenGl();
            GuiDraw.drawRect(x - 1, y - 1, width + 2, 1, 0xffdaffff);
            GuiDraw.drawRect(x - 1, y + height, width + 2, 1, 0xffdaffff);
            GuiDraw.drawRect(x - 1, y, 1, height, 0xffdaffff);
            GuiDraw.drawRect(x + width, y, 1, height, 0xffdaffff);
            GuiDraw.drawRect(x, y, width, height, 0x669cd3ff);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
