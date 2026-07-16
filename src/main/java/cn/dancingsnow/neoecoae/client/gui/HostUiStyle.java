package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.util.ResourceLocation;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class HostUiStyle {

    static final ResourceLocation BACKGROUND = texture("background");
    static final ResourceLocation SLOT = texture("slot");
    static final ResourceLocation BUTTON = texture("button");
    static final ResourceLocation BUTTON_HOVER = texture("button_hover");
    static final ResourceLocation BUTTON_DISABLED = texture("button_disabled");
    static final ResourceLocation BUTTON_HIGHLIGHTED = texture("button_highlighted");
    static final ResourceLocation STORAGE_CONTROLLER_ELEMENTS = texture("storage/estorage_controller_elements");

    static final int TEXT_PRIMARY = 0xFF404040;
    static final int HOST_TITLE = 0xFF3F3D52;
    static final int TEXT_SECONDARY = 0xFF606060;
    static final int TEXT_MUTED = 0xFF707070;
    static final int TEXT_HINT = 0xFF2A5080;
    static final int TEXT_GOOD = 0xFF1A6A3A;
    static final int TEXT_WARN = 0xFF7A5010;
    static final int TEXT_BAD = 0xFF8A1A2A;
    static final int TEXT_VALUE = 0xFF3A5A8A;
    static final int DARK_PANEL_OUTER = 0xFF17141E;
    static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    static final int DARK_PANEL_INNER = 0xFF665F6D;
    static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;
    static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    static final int DARK_TEXT_VALUE = 0xFF8377FF;
    static final int DARK_TEXT_USED = 0xFF00FC00;
    static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;
    static final int DARK_TEXT_WARNING = 0xFFFFD65A;
    static final int DARK_TEXT_BLUE = 0xFF3FD6FF;
    static final int DARK_TEXT_ORANGE = 0xFFFF9A3D;
    static final int DARK_TEXT_ERROR = 0xFFFF6A75;
    static final int ACCENT_L4 = 0xFFFFFF55;
    static final int ACCENT_L6 = 0xFF55FFFF;
    static final int ACCENT_L9 = 0xFFFF55FF;
    static final int MATRIX_USAGE_LOW = 0xFF45F05A;
    static final int MATRIX_USAGE_MEDIUM = 0xFFFFEA4A;
    static final int MATRIX_USAGE_HIGH = 0xFFFF9D32;
    static final int MATRIX_USAGE_FULL = 0xFFFF5151;
    static final int MATRIX_USAGE_INFINITE = 0xFFD8A8FF;
    static final int MATRIX_USAGE_EMPTY = 0xFF413E4E;

    private HostUiStyle() {}

    static int tierColor(String tier) {
        if ("L9".equalsIgnoreCase(tier) || "256M".equalsIgnoreCase(tier)) {
            return ACCENT_L9;
        }
        if ("L6".equalsIgnoreCase(tier) || "64M".equalsIgnoreCase(tier)) {
            return ACCENT_L6;
        }
        return ACCENT_L4;
    }

    static int matrixUsageColor(long used, long total) {
        if (total <= 0L) {
            return MATRIX_USAGE_LOW;
        }
        double ratio = (double) used / (double) total;
        if (ratio >= 0.9D) {
            return MATRIX_USAGE_FULL;
        }
        if (ratio >= 0.75D) {
            return MATRIX_USAGE_HIGH;
        }
        if (ratio >= 0.5D) {
            return MATRIX_USAGE_MEDIUM;
        }
        return MATRIX_USAGE_LOW;
    }

    static int matrixUsageHighlight(long used, long total) {
        return lerpColor(matrixUsageColor(used, total), 0xFFFFFFFF, 0.28D);
    }

    static int usedValueColor(long used, long total) {
        if (total <= 0L) {
            return DARK_TEXT_MUTED;
        }
        double ratio = (double) used / (double) total;
        if (ratio >= 1.0D) {
            return DARK_TEXT_ERROR;
        }
        if (ratio >= 0.9D) {
            return DARK_TEXT_ORANGE;
        }
        if (ratio >= 0.75D) {
            return DARK_TEXT_WARNING;
        }
        return DARK_TEXT_USED;
    }

    static int metricColor(int accentColor, long maximum, double percent) {
        if (maximum <= 0L) {
            return DARK_TEXT_MUTED;
        }
        return lerpColor(darken(accentColor, 0.72D), accentColor, clamp(percent + 0.2D, 0.0D, 1.0D));
    }

    static int storageGaugeColor(double percent, boolean reverse) {
        double amount = clamp(percent, 0.0D, 1.0D);
        if (reverse) {
            amount = 1.0D - amount;
        }
        if (amount < 0.5D) {
            return lerpColor(0xBF00FF00, 0xBFFFFF00, amount / 0.5D);
        }
        return lerpColor(0xBFFFFF00, 0xBFFF0000, (amount - 0.5D) / 0.5D);
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(NeoECOAE.MODID, "textures/gui/" + name + ".png");
    }

    private static int darken(int color, double factor) {
        int alpha = color >>> 24 & 0xFF;
        int red = (int) ((color >>> 16 & 0xFF) * factor);
        int green = (int) ((color >>> 8 & 0xFF) * factor);
        int blue = (int) ((color & 0xFF) * factor);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int lerpColor(int start, int end, double amount) {
        double value = clamp(amount, 0.0D, 1.0D);
        int alpha = (int) ((start >>> 24 & 0xFF) + ((end >>> 24 & 0xFF) - (start >>> 24 & 0xFF)) * value);
        int red = (int) ((start >>> 16 & 0xFF) + ((end >>> 16 & 0xFF) - (start >>> 16 & 0xFF)) * value);
        int green = (int) ((start >>> 8 & 0xFF) + ((end >>> 8 & 0xFF) - (start >>> 8 & 0xFF)) * value);
        int blue = (int) ((start & 0xFF) + ((end & 0xFF) - (start & 0xFF)) * value);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
