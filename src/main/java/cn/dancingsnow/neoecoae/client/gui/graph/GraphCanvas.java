package cn.dancingsnow.neoecoae.client.gui.graph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 1.7.10 drawing surface of the graph (the role GuiGraphics plays on 1.21). Colored quads are batched in one
 * Tessellator pass; text, item icons and matrix changes flush the batch first so the painter's order is kept.
 */
@SideOnly(Side.CLIENT)
final class GraphCanvas implements GraphDraw.QuadSink {

    final Minecraft mc;
    final FontRenderer font;
    private boolean drawing;

    GraphCanvas(Minecraft mc) {
        this.mc = mc;
        this.font = mc.fontRenderer;
    }

    @Override
    public void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int color) {
        Tessellator tessellator = Tessellator.instance;
        if (!drawing) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            tessellator.startDrawingQuads();
            drawing = true;
        }
        tessellator.setColorRGBA_I(color & 0xFFFFFF, color >>> 24);
        tessellator.addVertex(ax, ay, 0);
        tessellator.addVertex(bx, by, 0);
        tessellator.addVertex(cx, cy, 0);
        tessellator.addVertex(dx, dy, 0);
    }

    /** Draws every pending quad; required before any non-batched draw or matrix change. */
    void flush() {
        if (!drawing) return;
        drawing = false;
        Tessellator.instance.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

    void fill(float x1, float y1, float x2, float y2, int color) {
        GraphDraw.fill(this, x1, y1, x2, y2, color);
    }

    void line(float x1, float y1, float x2, float y2, int color, float halfWidth) {
        GraphDraw.line(this, x1, y1, x2, y2, color, halfWidth);
    }

    void push() {
        flush();
        GL11.glPushMatrix();
    }

    void pop() {
        flush();
        GL11.glPopMatrix();
    }

    void translate(float x, float y) {
        flush();
        GL11.glTranslatef(x, y, 0);
    }

    void scale(float x, float y) {
        flush();
        GL11.glScalef(x, y, 1);
    }

    int width(String text) {
        return font.getStringWidth(text);
    }

    int lineHeight() {
        return font.FONT_HEIGHT;
    }

    void text(String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        flush();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        font.drawString(text, x, y, color, false);
        GL11.glColor4f(1, 1, 1, 1);
    }

    void centered(String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) return;
        flush();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        font.drawStringWithShadow(text, centerX - font.getStringWidth(text) / 2, y, color);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Text cut to a pixel width with a trailing ellipsis, like 1.21's plainSubstrByWidth fit. */
    String fit(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.getStringWidth(value) <= maxWidth) return value;
        String ellipsis = "…";
        return font.trimStringToWidth(value, Math.max(0, maxWidth - font.getStringWidth(ellipsis))) + ellipsis;
    }

    /** One 16x16 stack icon of a key; fluids and items both go through AE2's own GUI renderer. */
    void item(AEKey key, int x, int y) {
        if (key == null) return;
        IAEStack<?> stack;
        try {
            stack = key.toNative(1);
        } catch (RuntimeException failure) {
            return;
        }
        if (stack == null) return;
        flush();
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        try {
            stack.drawInGui(mc, x, y);
        } catch (RuntimeException ignored) {
            // A broken item renderer must not take the whole graph down.
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glPopMatrix();
        }
    }
}
