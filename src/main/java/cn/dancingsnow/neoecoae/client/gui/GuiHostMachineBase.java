package cn.dancingsnow.neoecoae.client.gui;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
abstract class GuiHostMachineBase extends GuiContainer {

    private static final int SLOT_SIZE = 18;
    private static final int STORAGE_GAUGE_WIDTH = 32;
    private static final int STORAGE_GAUGE_CAP_HEIGHT = 8;
    private static final int STORAGE_GAUGE_TOP_U = 1;
    private static final int STORAGE_GAUGE_TOP_V = 246;
    private static final int STORAGE_GAUGE_MID_U = 34;
    private static final int STORAGE_GAUGE_MID_V = 250;
    private static final int STORAGE_GAUGE_MID_HEIGHT = 4;
    private static final int STORAGE_GAUGE_BOTTOM_U = 1;
    private static final int STORAGE_GAUGE_BOTTOM_V = 246;
    private static final int STORAGE_GAUGE_TEXTURE_SIZE = 256;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.US);

    GuiHostMachineBase(Container container, int width, int height) {
        super(container);
        this.xSize = width;
        this.ySize = height;
    }

    @Override
    protected final void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawNineSlice(
            HostUiStyle.BACKGROUND,
            this.guiLeft,
            this.guiTop,
            this.xSize,
            this.ySize,
            16,
            16,
            2,
            2,
            2,
            4);
        this.drawHostBackgroundLayer(partialTicks, mouseX, mouseY);
    }

    protected void drawHostBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    protected final void drawPlayerInventorySlots(int inventoryX, int inventoryY, int hotbarY) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.drawSlotTexture(
                    this.guiLeft + inventoryX + column * SLOT_SIZE,
                    this.guiTop + inventoryY + row * SLOT_SIZE);
            }
        }
        for (int column = 0; column < 9; column++) {
            this.drawSlotTexture(this.guiLeft + inventoryX + column * SLOT_SIZE, this.guiTop + hotbarY);
        }
    }

    protected final void drawSlotTexture(int x, int y) {
        this.drawTexture(
            HostUiStyle.SLOT,
            x,
            y,
            SLOT_SIZE,
            SLOT_SIZE,
            0,
            0,
            SLOT_SIZE,
            SLOT_SIZE,
            SLOT_SIZE,
            SLOT_SIZE);
    }

    protected final void drawDarkInsetRect(int x, int y, int width, int height) {
        this.drawNineSlice(
            HostPanelBorderTexture.location(),
            this.guiLeft + x,
            this.guiTop + y,
            width,
            height,
            16,
            16,
            6,
            6,
            6,
            6);
    }

    protected final void drawTinyInsetRect(int x, int y, int width, int height, int innerColor) {
        int left = this.guiLeft + x;
        int top = this.guiTop + y;
        drawRect(left, top, left + width, top + height, HostUiStyle.DARK_PANEL_LIGHT_EDGE);
        drawRect(left + 1, top + 1, left + width - 1, top + height - 1, HostUiStyle.DARK_PANEL_OUTER);
        drawRect(left + 2, top + 2, left + width - 2, top + height - 2, innerColor);
    }

    protected final void drawTinyInsetLocal(int x, int y, int width, int height, int innerColor) {
        drawRect(x, y, x + width, y + height, HostUiStyle.DARK_PANEL_LIGHT_EDGE);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, HostUiStyle.DARK_PANEL_OUTER);
        drawRect(x + 2, y + 2, x + width - 2, y + height - 2, innerColor);
    }

    /** Pixel-equivalent of the LDLib2 host inset button used by all three controllers. */
    protected final void drawInsetButtonLocal(int x, int y, int width, int height, boolean hovered,
        boolean pressed, boolean selected) {
        int edge = hovered ? 0xFFDAD5E8 : HostUiStyle.DARK_PANEL_LIGHT_EDGE;
        int middle = selected ? 0xFF3B3445 : 0xFF47434F;
        int inner = selected ? 0xFF282232 : 0xFF5A5460;
        if (pressed) {
            middle = 0xFF302A38;
            inner = 0xFF211C29;
        }
        drawRect(x, y, x + width, y + height, edge);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF0D0D11);
        drawRect(x + 2, y + 2, x + width - 2, y + height - 2, middle);
        drawRect(x + 3, y + 3, x + width - 3, y + height - 3, inner);
        if (pressed) {
            drawRect(x + 3, y + 3, x + width - 3, y + 4, 0x99000000);
        } else {
            drawRect(x + 3, y + 3, x + width - 3, y + 4, 0x55FFFFFF);
            drawRect(x + 3, y + height - 4, x + width - 3, y + height - 3, 0x99000000);
        }
        if (selected) {
            drawRect(x + 3, y + height - 4, x + width - 3, y + height - 3,
                HostUiStyle.DARK_TEXT_SUCCESS);
        }
    }

    protected final void drawDarkSlotLocal(int x, int y, int size) {
        drawRect(x, y, x + size, y + size, HostUiStyle.DARK_PANEL_MIDDLE);
        drawRect(x, y, x + size, y + 1, 0xFF0D0D11);
        drawRect(x, y, x + 1, y + size, 0xFF0D0D11);
        drawRect(x, y + size - 1, x + size, y + size, HostUiStyle.DARK_PANEL_LIGHT_EDGE);
        drawRect(x + size - 1, y, x + size, y + size, HostUiStyle.DARK_PANEL_LIGHT_EDGE);
        drawRect(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF4B4653);
        drawRect(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF5A5460);
    }

    final void drawLocalRect(int x, int y, int width, int height, int color) {
        drawRect(this.guiLeft + x, this.guiTop + y, this.guiLeft + x + width, this.guiTop + y + height, color);
    }

    protected final void drawUsageBar(int x, int y, int width, int height, long value, long max, int color) {
        this.drawTinyInsetRect(x, y, width, height, 0xFF201E27);
        int filled = this.ratioSize(value, max, width - 4);
        if (filled > 0) {
            drawRect(
                this.guiLeft + x + 2,
                this.guiTop + y + 2,
                this.guiLeft + x + 2 + filled,
                this.guiTop + y + height - 2,
                color);
        }
    }

    protected final void drawUsageBarLocal(int x, int y, int width, int height, long value, long max, int color) {
        this.drawTinyInsetLocal(x, y, width, height, 0xFF201E27);
        int filled = this.ratioSize(value, max, width - 4);
        if (filled > 0) {
            drawRect(x + 2, y + 2, x + 2 + filled, y + height - 2, color);
        }
    }

    protected final void drawVerticalGauge(int x, int y, int width, int height, long value, long max, int color) {
        this.drawTinyInsetRect(x, y, width, height, 0xFF201E27);
        int filled = this.ratioSize(value, max, height - 4);
        if (filled > 0) {
            int bottom = this.guiTop + y + height - 2;
            drawRect(this.guiLeft + x + 2, bottom - filled, this.guiLeft + x + width - 2, bottom, color);
        }
    }

    protected final void drawStorageGauge(int x, int y, double percentage, boolean reverseColor) {
        double clamped = Math.max(0.0D, Math.min(1.0D, percentage));
        this.drawStorageGauge(x, y, STORAGE_GAUGE_WIDTH, 143, clamped,
            HostUiStyle.storageGaugeColor(clamped, reverseColor));
    }

    protected final void drawStorageGauge(int x, int y, double percentage, int color) {
        this.drawStorageGauge(x, y, STORAGE_GAUGE_WIDTH, 143, percentage, color);
    }

    protected final void drawStorageGauge(int x, int y, int width, int height, double percentage, int color) {
        double clamped = Math.max(0.0D, Math.min(1.0D, percentage));
        if (clamped <= 0.0D) {
            return;
        }

        int left = x;
        int top = y;
        int bodyHeight = height - STORAGE_GAUGE_CAP_HEIGHT;
        int barHeight = (int) Math.round(bodyHeight * clamped);
        float alpha = (float) (color >>> 24 & 0xFF) / 255.0F;
        float red = (float) (color >>> 16 & 0xFF) / 255.0F;
        float green = (float) (color >>> 8 & 0xFF) / 255.0F;
        float blue = (float) (color & 0xFF) / 255.0F;

        GL11.glColor4f(red, green, blue, alpha);
        this.drawTintedTexture(
            HostUiStyle.STORAGE_CONTROLLER_ELEMENTS,
            left,
            top + height - barHeight - STORAGE_GAUGE_CAP_HEIGHT,
            width,
            STORAGE_GAUGE_CAP_HEIGHT,
            STORAGE_GAUGE_TOP_U,
            STORAGE_GAUGE_TOP_V,
            STORAGE_GAUGE_WIDTH,
            STORAGE_GAUGE_CAP_HEIGHT,
            STORAGE_GAUGE_TEXTURE_SIZE,
            STORAGE_GAUGE_TEXTURE_SIZE);

        int midStart = top + height - barHeight - STORAGE_GAUGE_CAP_HEIGHT / 2 + 1;
        int midEnd = top + height - STORAGE_GAUGE_CAP_HEIGHT + STORAGE_GAUGE_CAP_HEIGHT / 2 + 1;
        for (int drawY = midStart; drawY < midEnd; drawY++) {
            this.drawTintedTexture(
                HostUiStyle.STORAGE_CONTROLLER_ELEMENTS,
                left,
                drawY,
                width,
                STORAGE_GAUGE_MID_HEIGHT,
                STORAGE_GAUGE_MID_U,
                STORAGE_GAUGE_MID_V,
                STORAGE_GAUGE_WIDTH,
                STORAGE_GAUGE_MID_HEIGHT,
                STORAGE_GAUGE_TEXTURE_SIZE,
                STORAGE_GAUGE_TEXTURE_SIZE);
        }

        this.drawTintedTexture(
            HostUiStyle.STORAGE_CONTROLLER_ELEMENTS,
            left,
            top + height - STORAGE_GAUGE_CAP_HEIGHT,
            width,
            STORAGE_GAUGE_CAP_HEIGHT,
            STORAGE_GAUGE_BOTTOM_U,
            STORAGE_GAUGE_BOTTOM_V,
            STORAGE_GAUGE_WIDTH,
            STORAGE_GAUGE_CAP_HEIGHT,
            STORAGE_GAUGE_TEXTURE_SIZE,
            STORAGE_GAUGE_TEXTURE_SIZE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected final void drawButtonTexture(int x, int y, int width, int height, boolean hovered, boolean enabled,
        boolean selected) {
        ResourceLocation texture = !enabled ? HostUiStyle.BUTTON_DISABLED
            : selected ? HostUiStyle.BUTTON_HIGHLIGHTED : hovered ? HostUiStyle.BUTTON_HOVER : HostUiStyle.BUTTON;
        this.drawNineSlice(texture, this.guiLeft + x, this.guiTop + y, width, height, 20, 20, 3, 3, 3, 3);
    }

    protected final void drawLocalText(String text, int x, int y, int color) {
        this.fontRendererObj.drawString(text, x, y, color);
    }

    protected final int drawLocalSegment(String text, int x, int y, int color) {
        this.fontRendererObj.drawString(text, x, y, color);
        return this.fontRendererObj.getStringWidth(text);
    }

    protected final void drawLocalCentered(String text, int x, int y, int width, int color) {
        this.fontRendererObj.drawString(text, x + (width - this.fontRendererObj.getStringWidth(text)) / 2, y, color);
    }

    protected final void drawLocalRight(String text, int rightX, int y, int color) {
        this.fontRendererObj.drawString(text, rightX - this.fontRendererObj.getStringWidth(text), y, color);
    }

    protected final void drawLocalCenteredScaled(String text, int x, int y, int width, int height, int color,
        float maxScale) {
        int textWidth = Math.max(1, this.fontRendererObj.getStringWidth(text));
        float scale = Math.min(maxScale, Math.max(0.55F, (float) (width - 4) / textWidth));
        GL11.glPushMatrix();
        GL11.glTranslatef(
            x + (width - textWidth * scale) / 2.0F,
            y + (height - this.fontRendererObj.FONT_HEIGHT * scale) / 2.0F,
            200.0F);
        GL11.glScalef(scale, scale, 1.0F);
        this.fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    protected final void drawTooltip(List<String> lines, int mouseX, int mouseY) {
        this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
    }

    protected final void drawLocalItemIcon(ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        float previousZLevel = this.itemRender.zLevel;
        GL11.glPushMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        this.itemRender.zLevel = 200.0F;
        this.itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, x, y);
        this.itemRender.zLevel = previousZLevel;
        RenderHelper.disableStandardItemLighting();
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Screen-space hit test for mouse values from drawScreen, mouseClicked, and handleMouseInput.
     * Note: drawGuiContainerForegroundLayer also receives screen-space mouse coordinates (the GL
     * matrix is translated by guiLeft/guiTop for drawing, but the mouse args are NOT), so use this
     * method there too.
     */
    protected final boolean isMouseIn(int x, int y, int width, int height, int mouseX, int mouseY) {
        int left = this.guiLeft + x;
        int top = this.guiTop + y;
        return isPointInRect(left, top, width, height, mouseX, mouseY);
    }

    private static boolean isPointInRect(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    protected final void beginScissor(int x, int y, int width, int height) {
        ScaledResolution resolution = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int scale = resolution.getScaleFactor();
        int screenX = (this.guiLeft + x) * scale;
        int screenY = this.mc.displayHeight - (this.guiTop + y + height) * scale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(screenX, screenY, Math.max(0, width * scale), Math.max(0, height * scale));
    }

    protected final void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    protected final String translate(String key, Object... args) {
        String translated = StatCollector.translateToLocalFormatted(key, args);
        return key.equals(translated) ? key : translated;
    }

    protected final String yesNo(boolean value) {
        String key = value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no";
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? (value ? "Yes" : "No") : translated;
    }

    protected final String formatNumber(long value) {
        return this.numberFormat.format(Math.max(0L, value));
    }

    protected final String formatStorageBytes(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1024L) {
            return this.formatNumber(safe) + " B";
        }
        String[] units = { "KiB", "MiB", "GiB", "TiB", "PiB" };
        double scaled = safe;
        int unit = -1;
        do {
            scaled /= 1024.0D;
            unit++;
        } while (scaled >= 1024.0D && unit < units.length - 1);
        String valueText = scaled >= 100.0D ? String.format(Locale.US, "%.0f", scaled)
            : scaled >= 10.0D ? String.format(Locale.US, "%.1f", scaled) : String.format(Locale.US, "%.2f", scaled);
        return valueText.replaceAll("\\.?0+$", "") + " " + units[unit];
    }

    private int ratioSize(long value, long max, int fullSize) {
        if (value <= 0L || max <= 0L || fullSize <= 0) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(fullSize, value * fullSize / max));
    }

    private void drawNineSlice(ResourceLocation texture, int x, int y, int width, int height, int textureWidth,
        int textureHeight, int left, int top, int right, int bottom) {
        int centerWidth = Math.max(0, width - left - right);
        int centerHeight = Math.max(0, height - top - bottom);
        int textureCenterWidth = textureWidth - left - right;
        int textureCenterHeight = textureHeight - top - bottom;
        this.drawTexture(texture, x, y, left, top, 0, 0, left, top, textureWidth, textureHeight);
        this.drawTexture(
            texture,
            x + left,
            y,
            centerWidth,
            top,
            left,
            0,
            textureCenterWidth,
            top,
            textureWidth,
            textureHeight);
        this.drawTexture(
            texture,
            x + width - right,
            y,
            right,
            top,
            textureWidth - right,
            0,
            right,
            top,
            textureWidth,
            textureHeight);
        this.drawTexture(
            texture,
            x,
            y + top,
            left,
            centerHeight,
            0,
            top,
            left,
            textureCenterHeight,
            textureWidth,
            textureHeight);
        this.drawTexture(
            texture,
            x + left,
            y + top,
            centerWidth,
            centerHeight,
            left,
            top,
            textureCenterWidth,
            textureCenterHeight,
            textureWidth,
            textureHeight);
        this.drawTexture(
            texture,
            x + width - right,
            y + top,
            right,
            centerHeight,
            textureWidth - right,
            top,
            right,
            textureCenterHeight,
            textureWidth,
            textureHeight);
        this.drawTexture(
            texture,
            x,
            y + height - bottom,
            left,
            bottom,
            0,
            textureHeight - bottom,
            left,
            bottom,
            textureWidth,
            textureHeight);
        this.drawTexture(
            texture,
            x + left,
            y + height - bottom,
            centerWidth,
            bottom,
            left,
            textureHeight - bottom,
            textureCenterWidth,
            bottom,
            textureWidth,
            textureHeight);
        this.drawTexture(
            texture,
            x + width - right,
            y + height - bottom,
            right,
            bottom,
            textureWidth - right,
            textureHeight - bottom,
            right,
            bottom,
            textureWidth,
            textureHeight);
    }

    protected final void drawTexture(ResourceLocation texture, int x, int y, int width, int height, int u, int v,
        int uWidth, int vHeight, int textureWidth, int textureHeight) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawTintedTexture(texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    final void drawLocalTexture(ResourceLocation texture, int x, int y, int width, int height, int u, int v, int uWidth,
        int vHeight, int textureWidth, int textureHeight) {
        this.drawTexture(
            texture,
            this.guiLeft + x,
            this.guiTop + y,
            width,
            height,
            u,
            v,
            uWidth,
            vHeight,
            textureWidth,
            textureHeight);
    }

    private void drawTintedTexture(ResourceLocation texture, int x, int y, int width, int height, int u, int v,
        int uWidth, int vHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || uWidth <= 0 || vHeight <= 0) {
            return;
        }
        TextureManager manager = this.mc.getTextureManager();
        manager.bindTexture(texture);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        double minU = (double) u / (double) textureWidth;
        double maxU = (double) (u + uWidth) / (double) textureWidth;
        double minV = (double) v / (double) textureHeight;
        double maxV = (double) (v + vHeight) / (double) textureHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, this.zLevel, minU, maxV);
        tessellator.addVertexWithUV(x + width, y + height, this.zLevel, maxU, maxV);
        tessellator.addVertexWithUV(x + width, y, this.zLevel, maxU, minV);
        tessellator.addVertexWithUV(x, y, this.zLevel, minU, minV);
        tessellator.draw();
    }
}
