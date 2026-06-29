package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.util.ResourceLocation;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class NEGuiTextures {

    public static final ResourceLocation BACKGROUND = texture("background");
    public static final ResourceLocation SLOT = texture("slot");
    public static final ResourceLocation BUTTON = texture("button");
    public static final ResourceLocation BUTTON_DISABLED = texture("button_disabled");
    public static final ResourceLocation BUTTON_HOVER = texture("button_hover");
    public static final ResourceLocation BUTTON_HIGHLIGHTED = texture("button_highlighted");
    public static final ResourceLocation CARD_BACKGROUND = texture("card_background");
    public static final ResourceLocation INVENTORY_BORDER = texture("inventory_border");
    public static final ResourceLocation BAR = texture("bar");
    public static final ResourceLocation BAR_CONTAINER = texture("bar_container");
    public static final ResourceLocation UPLOAD = texture("upload");

    public static final int TEXT_PRIMARY = 0xE6E6F0;
    public static final int TEXT_MUTED = 0x9D98AA;
    public static final int TEXT_VALUE = 0xFFFFFF55;
    public static final int TEXT_GOOD = 0x55FF55;
    public static final int TEXT_WARN = 0xFFAA00;
    public static final int TEXT_BAD = 0xFF5555;
    public static final int PANEL_OUTER = 0xFF17141E;
    public static final int PANEL_MIDDLE = 0xFF24202D;
    public static final int PANEL_INSET = 0xFF302C38;
    public static final int PANEL_HOVER = 0xFF3B3645;
    public static final int ACCENT_L4 = 0xFFFFFF55;
    public static final int ACCENT_L6 = 0xFF55FFFF;
    public static final int ACCENT_L9 = 0xFFFF55FF;

    private NEGuiTextures() {}

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(NeoECOAE.MODID, "textures/gui/" + name + ".png");
    }
}
