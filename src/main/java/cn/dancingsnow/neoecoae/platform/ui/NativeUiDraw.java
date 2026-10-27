package cn.dancingsnow.neoecoae.platform.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.GuiDraw;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The source artwork's pixel coordinates, text scale and alignment on the native MUI2 canvas. */
@SideOnly(Side.CLIENT)
public final class NativeUiDraw {

    private NativeUiDraw() {}

    public static void sprite(String atlas, int u, int v, int sw, int sh, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || sw <= 0 || sh <= 0) return;
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        NativePageLayout.texture(atlas, u, v, sw, sh)
            .draw(x, y, w, h);
    }

    public static void text(Component value, float x, float y, float width, float preferredScale, int color,
        boolean centered, boolean shadow) {
        if (value == null || width <= 0) return;
        var font = Minecraft.getMinecraft().fontRenderer;
        String text = value.nativeText()
            .getFormattedText();
        if (text.isEmpty()) return;
        float scale = Math.min(preferredScale, width / Math.max(1, font.getStringWidth(text)));
        float left = x + (centered ? (width - font.getStringWidth(text) * scale) / 2 : 0);
        com.cleanroommc.modularui.utils.Platform.setupDrawFont();
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            org.lwjgl.opengl.GL11.glTranslatef(left, y, 0);
            org.lwjgl.opengl.GL11.glScalef(scale, scale, 1);
            int[] cursor = { 0 };
            value.visit((style, segment) -> {
                int actual = style.getColor() == null ? color
                    : style.getColor()
                        .getValue();
                if (shadow) font.drawStringWithShadow(segment, cursor[0], 0, actual);
                else font.drawString(segment, Math.round(cursor[0]), 0, actual);
                cursor[0] += font.getStringWidth(segment);
                return java.util.Optional.empty();
            }, Component.TextStyle.EMPTY);
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        }
    }

    public static void item(ItemStack stack, int x, int y, float z) {
        if (ItemStacks.isEmpty(stack)) return;
        GuiDraw.drawItem(stack, x, y, 16, 16, Math.round(z));
        RenderHelper.disableStandardItemLighting();
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
    }
}
