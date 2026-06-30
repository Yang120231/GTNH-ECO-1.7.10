package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;

final class AEA2ToolbarIconButton {

    private static final ResourceLocation STATES = new ResourceLocation(
        "appliedenergistics2",
        "textures/guis/states.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int ICON_SIZE = 16;
    private static final Sprite BACKGROUND = new Sprite(240, 240, 16, 16);
    static final Sprite TYPE_FILTER_FLUIDS = new Sprite(144, 16, 16, 16);
    static final Sprite CONDENSER_OUTPUT_TRASH = new Sprite(0, 112, 16, 16);
    static final Sprite POWER_UNIT_AE = new Sprite(0, 160, 16, 16);

    private AEA2ToolbarIconButton() {}

    static void draw(GuiHostMachineBase gui, int x, int y, int mouseX, int mouseY, int size, Sprite icon,
        boolean enabled, boolean selected) {
        boolean hovered = gui.isMouseIn(x, y, size, size, mouseX, mouseY);
        boolean pressed = enabled && hovered && Mouse.isButtonDown(0);
        int yOffset = pressed ? 1 : 0;
        gui.drawLocalTexture(
            STATES,
            x,
            y,
            BACKGROUND.width,
            BACKGROUND.height,
            BACKGROUND.u,
            BACKGROUND.v,
            BACKGROUND.width,
            BACKGROUND.height,
            TEXTURE_SIZE,
            TEXTURE_SIZE);
        if (!enabled) {
            drawDisabledOverlay(gui, x, y, size);
        }
        gui.drawLocalTexture(
            STATES,
            x,
            y + yOffset,
            ICON_SIZE,
            ICON_SIZE,
            icon.u,
            icon.v,
            icon.width,
            icon.height,
            TEXTURE_SIZE,
            TEXTURE_SIZE);
    }

    private static void drawDisabledOverlay(GuiHostMachineBase gui, int x, int y, int size) {
        gui.drawLocalRect(x, y, size, size, 0x66000000);
    }

    static final class Sprite {

        private final int u;
        private final int v;
        private final int width;
        private final int height;

        private Sprite(int u, int v, int width, int height) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }
    }
}
