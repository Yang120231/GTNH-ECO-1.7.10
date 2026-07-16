package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;

final class AEA2ToolbarIconButton {

    private static final ResourceLocation STATES = new ResourceLocation("neoecoae", "textures/gui/ae2/states.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int ICON_SIZE = 16;
    private static final Sprite BACKGROUND = new Sprite(176, 128, 18, 20);
    private static final Sprite BACKGROUND_SELECTED = new Sprite(194, 128, 18, 20);
    private static final Sprite BACKGROUND_HOVER = new Sprite(212, 128, 18, 20);
    static final Sprite TYPE_FILTER_ALL = new Sprite(160, 16, 16, 16);
    static final Sprite CRAFT_HAMMER = new Sprite(48, 144, 16, 16);
    static final Sprite POWER_UNIT_AE = new Sprite(0, 160, 16, 16);
    static final Sprite S_TERMINAL = new Sprite(192, 224, 10, 10);
    static final Sprite S_MACHINE = new Sprite(192, 234, 10, 10);

    private AEA2ToolbarIconButton() {}

    static void draw(GuiHostMachineBase gui, int x, int y, int mouseX, int mouseY, int size, Sprite icon,
        boolean selected) {
        boolean hovered = gui.isMouseIn(x, y, size, size, mouseX, mouseY);
        boolean pressed = hovered && Mouse.isButtonDown(0);
        int yOffset = selected || pressed ? 1 : 0;
        Sprite background = selected ? BACKGROUND_SELECTED : hovered ? BACKGROUND_HOVER : BACKGROUND;
        gui.drawLocalTexture(
            STATES,
            x - 1,
            y - 2,
            background.width,
            background.height,
            background.u,
            background.v,
            background.width,
            background.height,
            TEXTURE_SIZE,
            TEXTURE_SIZE);
        int iconDrawSize = icon.width < ICON_SIZE || icon.height < ICON_SIZE ? 12 : ICON_SIZE;
        int iconOffset = (size - iconDrawSize) / 2;
        gui.drawLocalTexture(
            STATES,
            x + iconOffset,
            y + iconOffset + yOffset,
            iconDrawSize,
            iconDrawSize,
            icon.u,
            icon.v,
            icon.width,
            icon.height,
            TEXTURE_SIZE,
            TEXTURE_SIZE);
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
