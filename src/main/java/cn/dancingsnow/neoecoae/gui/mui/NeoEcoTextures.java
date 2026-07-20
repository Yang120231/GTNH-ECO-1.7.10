package cn.dancingsnow.neoecoae.gui.mui;

import com.cleanroommc.modularui.drawable.UITexture;

import cn.dancingsnow.neoecoae.NeoECOAE;

/** Textures shared by the MUI2 implementation of the original host screens. */
final class NeoEcoTextures {

    static final UITexture BACKGROUND = texture("background", 16, 16).adaptable(2, 2, 2, 4)
        .build();
    static final UITexture SLOT = texture("slot", 18, 18).adaptable(1)
        .build();
    static final UITexture PANEL_BORDER = texture("crafting/panel_border", 16, 16).adaptable(3)
        .build();
    static final UITexture PANEL = texture("crafting/panel_background", 16, 16).tiled()
        .build();
    static final UITexture CARD = texture("card_background", 16, 16).tiled()
        .build();
    static final UITexture BUTTON = texture("button", 20, 20).adaptable(3)
        .build();
    static final UITexture BUTTON_HOVER = texture("button_hover", 20, 20).adaptable(3)
        .build();
    static final UITexture BUTTON_DISABLED = texture("button_disabled", 20, 20).adaptable(3)
        .build();
    static final UITexture BUTTON_SELECTED = texture("button_highlighted", 20, 20).adaptable(3)
        .build();

    static final UITexture STATES = texture("ae2/states", 256, 256).build();
    /** LDLib2's default rounded button sprites (gdp_styles.png, 13x13 source cells). */
    static final UITexture RECT_RD = texture("ldlib2/gdp_styles", 256, 256).subAreaXYWH(1, 29, 13, 13)
        .adaptable(4)
        .build();
    static final UITexture RECT_RD_LIGHT = texture("ldlib2/gdp_styles", 256, 256).subAreaXYWH(1, 15, 13, 13)
        .adaptable(4)
        .build();
    static final UITexture RECT_RD_DARK = texture("ldlib2/gdp_styles", 256, 256).subAreaXYWH(1, 43, 13, 13)
        .adaptable(4)
        .build();
    static final UITexture TOOLBAR = STATES.getSubArea(176f / 256f, 128f / 256f, 194f / 256f, 148f / 256f);
    static final UITexture TOOLBAR_SELECTED = STATES.getSubArea(194f / 256f, 128f / 256f, 212f / 256f, 148f / 256f);
    static final UITexture TOOLBAR_HOVER = STATES.getSubArea(212f / 256f, 128f / 256f, 230f / 256f, 148f / 256f);
    static final UITexture HAMMER = STATES.getSubArea(48f / 256f, 144f / 256f, 64f / 256f, 160f / 256f);
    static final UITexture POWER = STATES.getSubArea(0f, 160f / 256f, 16f / 256f, 176f / 256f);
    static final UITexture FILTER = STATES.getSubArea(160f / 256f, 16f / 256f, 176f / 256f, 32f / 256f);

    static final UITexture CRAFTING_PROGRESS = texture("crafting/crafting_progress", 9, 28).build();
    static final UITexture COOLANT_PROGRESS = texture("crafting/coolant_progress", 27, 36).build();
    static final UITexture HOT_COOLANT_PROGRESS = texture("crafting/hot_coolant_progress", 27, 36).build();
    static final UITexture STORAGE_GAUGE_CAP = texture("storage/estorage_controller_elements", 256, 256)
        .subAreaXYWH(1, 246, 32, 8)
        .nonOpaque()
        .build();
    static final UITexture STORAGE_GAUGE_MIDDLE = texture("storage/estorage_controller_elements", 256, 256)
        .subAreaXYWH(34, 250, 32, 4)
        .nonOpaque()
        .build();

    private NeoEcoTextures() {}

    private static UITexture.Builder texture(String path, int width, int height) {
        return UITexture.builder()
            .location(NeoECOAE.MODID, "gui/" + path)
            .imageSize(width, height);
    }
}
