package cn.dancingsnow.neoecoae.platform.ui.upload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** ECO's GUI sprites (see {@code NativeUiStyle}) drawn as 9-slices for plain {@code GuiScreen}s. */
@SideOnly(Side.CLIENT)
final class UploadDraw {

    static final ResourceLocation BACKGROUND = new ResourceLocation("neoecoae", "textures/gui/common/background.png");
    static final ResourceLocation BUTTON = new ResourceLocation("neoecoae", "textures/gui/common/button.png");
    static final ResourceLocation BUTTON_HIGHLIGHTED = new ResourceLocation(
        "neoecoae",
        "textures/gui/common/button_highlighted.png");
    static final ResourceLocation SLOT = new ResourceLocation("neoecoae", "textures/gui/common/slot.png");

    private UploadDraw() {}

    static void panel(int x, int y, int width, int height) {
        nineSlice(BACKGROUND, 16, 16, x, y, width, height, 2, 2, 2, 4);
    }

    static void button(int x, int y, int width, int height, boolean highlighted) {
        if (highlighted) nineSlice(BUTTON_HIGHLIGHTED, 20, 20, x, y, width, height, 2, 3, 2, 5);
        else nineSlice(BUTTON, 20, 20, x, y, width, height, 2, 2, 2, 5);
    }

    static void slot(int x, int y) {
        nineSlice(SLOT, 18, 18, x, y, 18, 18, 1, 1, 1, 1);
    }

    /** Draws a {@code texWidth x texHeight} texture stretched to the box, keeping the given borders unscaled. */
    static void nineSlice(ResourceLocation texture, int texWidth, int texHeight, int x, int y, int width, int height,
        int left, int top, int right, int bottom) {
        if (width <= 0 || height <= 0) return;
        left = Math.min(left, width / 2);
        right = Math.min(right, width - left);
        top = Math.min(top, height / 2);
        bottom = Math.min(bottom, height - top);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float u1 = left / (float) texWidth, u2 = (texWidth - right) / (float) texWidth;
        float v1 = top / (float) texHeight, v2 = (texHeight - bottom) / (float) texHeight;
        int x1 = x + left, x2 = x + width - right, x3 = x + width;
        int y1 = y + top, y2 = y + height - bottom, y3 = y + height;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        quad(tessellator, x, y, x1, y1, 0, 0, u1, v1);
        quad(tessellator, x1, y, x2, y1, u1, 0, u2, v1);
        quad(tessellator, x2, y, x3, y1, u2, 0, 1, v1);
        quad(tessellator, x, y1, x1, y2, 0, v1, u1, v2);
        quad(tessellator, x1, y1, x2, y2, u1, v1, u2, v2);
        quad(tessellator, x2, y1, x3, y2, u2, v1, 1, v2);
        quad(tessellator, x, y2, x1, y3, 0, v2, u1, 1);
        quad(tessellator, x1, y2, x2, y3, u1, v2, u2, 1);
        quad(tessellator, x2, y2, x3, y3, u2, v2, 1, 1);
        tessellator.draw();
    }

    private static void quad(Tessellator tessellator, int x0, int y0, int x1, int y1, float u0, float v0, float u1,
        float v1) {
        if (x1 <= x0 || y1 <= y0) return;
        tessellator.addVertexWithUV(x0, y1, 0, u0, v1);
        tessellator.addVertexWithUV(x1, y1, 0, u1, v1);
        tessellator.addVertexWithUV(x1, y0, 0, u1, v0);
        tessellator.addVertexWithUV(x0, y0, 0, u0, v0);
    }

    /** Solid rectangle that leaves the GL colour white afterwards ({@link Gui#drawRect} leaves it tinted). */
    static void rect(int x0, int y0, int x1, int y1, int argb) {
        Gui.drawRect(x0, y0, x1, y1, argb);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /**
     * Pixel-art glyph: each string is one row, '.' is transparent and every other character is looked up in
     * {@code colors} as {@code char, argb} pairs. Runs of one colour are drawn as one rectangle.
     */
    static void glyph(int x, int y, String[] rows, Object... colors) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int start = 0;
            while (start < line.length()) {
                char c = line.charAt(start);
                int end = start + 1;
                while (end < line.length() && line.charAt(end) == c) end++;
                if (c != '.') {
                    int color = color(c, colors);
                    if (color != 0) Gui.drawRect(x + start, y + row, x + end, y + row + 1, color);
                }
                start = end;
            }
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static int color(char c, Object[] colors) {
        for (int index = 0; index + 1 < colors.length; index += 2)
            if (colors[index] instanceof Character && (Character) colors[index] == c)
                return (Integer) colors[index + 1];
        return 0;
    }
}
