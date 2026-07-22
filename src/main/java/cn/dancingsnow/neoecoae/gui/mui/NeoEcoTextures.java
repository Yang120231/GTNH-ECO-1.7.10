package cn.dancingsnow.neoecoae.gui.mui;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

import cn.dancingsnow.neoecoae.NeoECOAE;

/** Textures shared by the MUI2 implementation of the original host screens. */
final class NeoEcoTextures {

    static final UITexture BACKGROUND = texture("background", 16, 16).adaptable(2, 2, 2, 4)
        .build();
    static final UITexture SLOT = texture("slot", 18, 18).adaptable(1)
        .build();
    static final UITexture PANEL_BORDER = texture("crafting/panel_border", 16, 16).adaptable(3)
        .build();
    static final UITexture PATTERN_OVERLAY = texture("widget/pattern_overlay", 18, 18).build();
    static final IDrawable PATTERN_OVERLAY_ALIGNED = (GuiContext context, int x, int y, int width, int height,
        WidgetTheme theme) -> PATTERN_OVERLAY.draw(context, x, y + 1, width, height, theme);
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

    static final UITexture STATES = UITexture.builder()
        .location(new ResourceLocation("appliedenergistics2", "textures/guis/states.png"))
        .imageSize(256, 256)
        .build();
    static final UITexture AE2_TERMINAL_STYLE_BUTTON = STATES
        .getSubArea(240f / 256f, 240f / 256f, 256f / 256f, 256f / 256f);
    static final UITexture AE2_TERMINAL_STYLE_TALL = STATES.getSubArea(0f, 208f / 256f, 16f / 256f, 224f / 256f);
    static final UITexture AE2_TERMINAL_STYLE_SMALL = STATES
        .getSubArea(16f / 256f, 208f / 256f, 32f / 256f, 224f / 256f);
    static final UITexture AE2_TERMINAL_STYLE_FULL = STATES
        .getSubArea(32f / 256f, 208f / 256f, 48f / 256f, 224f / 256f);
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
