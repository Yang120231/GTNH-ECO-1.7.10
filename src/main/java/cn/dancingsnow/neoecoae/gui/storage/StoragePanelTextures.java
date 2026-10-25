package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.ui.NativeSpriteTexture;

public final class StoragePanelTextures {

    public static final int VALUE_TEXT = 0x32204F;

    private static final ResourceLocation ATLAS = id("storage_widget.png");

    private static final int DATA_SWATCH = 14;

    private static final int RADIOACTIVE_SWATCH = 15;

    private static final int STRESS_SWATCH = 16;

    private static final int MATTER_SWATCH = 17;

    static final int FALLBACK_SWATCH = 18;

    static final int[] COLORS = { 0x17B86D, 0x3894FF, 0xEC8600, 0xCCCCCC, 0xCC493D, 0xFFB2D8, 0x72B036, 0x3AD8CB,
        0xC93BED, 0x06DFFF, 0x2CCDB1, 0xCC4BBD, 0x6056AF, 0x6F8259, 0x15BFAE, 0x508457, 0xEFC700, 0x72E567, 0xCCCCCC };

    private static ResourceLocation id(String file) {
        return ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/guis/storage/" + file);
    }

    static NativeSpriteTexture sprite(int x, int y, int width, int height) {
        return NativeSpriteTexture.of(ATLAS)
            .setSprite(x, y, width, height);
    }

    public static NativeSpriteTexture swatch(int index, int color) {
        if (index < 0 || index >= FALLBACK_SWATCH) return sprite(16, 78, 8, 8).setBorder(1, 0, 1, 0)
            .setColor(0xFF000000 | color & 0xFFFFFF);
        if (index == DATA_SWATCH) return sprite(48, 70, 8, 8).setBorder(1, 0, 1, 0);
        if (index == RADIOACTIVE_SWATCH) return sprite(96, 62, 8, 8).setBorder(1, 0, 1, 0);
        if (index == STRESS_SWATCH) return sprite(104, 62, 8, 8).setBorder(1, 0, 1, 0);
        if (index == MATTER_SWATCH) return sprite(56, 70, 8, 8).setBorder(1, 0, 1, 0);
        if (index < 6) {
            return sprite(16 + index * 8, 62, 8, 8).setBorder(1, 0, 1, 0);
        } else if (index < 10) {
            return sprite(16 + (index - 6) * 8, 70, 8, 8).setBorder(1, 0, 1, 0);
        } else {
            return sprite(64 + (index - 10) * 8, 62, 8, 8).setBorder(1, 0, 1, 0);
        }
    }

    public static NativeSpriteTexture resourceIcon(int index, int color) {
        if (index < 0 || index >= FALLBACK_SWATCH) return swatch(index, color);
        if (index == DATA_SWATCH) return sprite(48, 94, 8, 8);
        if (index == RADIOACTIVE_SWATCH) return sprite(96, 86, 8, 8);
        if (index == STRESS_SWATCH) return sprite(104, 86, 8, 8);
        if (index == MATTER_SWATCH) return sprite(56, 94, 8, 8);
        if (index < 6) {
            return sprite(16 + index * 8, 86, 8, 8);
        } else if (index < 10) {
            return sprite(16 + (index - 6) * 8, 94, 8, 8);
        } else {
            return sprite(64 + (index - 10) * 8, 86, 8, 8);
        }
    }

    static int swatchIndex(ResourceLocation id) {
        if (id == null) return FALLBACK_SWATCH;
        return switch (id.toString()) {
            case "neoecoae:mekanism", "neoecoae:mega_chemical", "neoecoae:bigger_ae2_chemical", "appmek:chemical" -> 0;
            case "neoecoae:fluids", "neoecoae:mega_fluid", "neoecoae:bigger_ae2_fluid", "ae2:f" -> 1;
            case "neoecoae:items", "neoecoae:bulk_item", "neoecoae:mega_item", "neoecoae:bigger_ae2_item", "ae2:i" -> 2;
            case "neoecoae:air", "neoecoae:mega_air", "appliedpneumatics:air_type" -> 3;
            case "neoecoae:flux", "neoecoae:mega_energy", "neoecoae:bigger_ae2_fe", "appflux:flux" -> 4;
            case "neoecoae:lightning", "ae2lt:lightning" -> 5;
            case "neoecoae:experience", "neoecoae:mega_experience", "neoecoae:bigger_ae2_experience", "appex:experience" -> 6;
            case "neoecoae:soul", "neoecoae:mega_soul", "soulplied_energistics:soul", "appliedsoul:soul" -> 7;
            case "neoecoae:source", "neoecoae:mega_source", "neoecoae:bigger_ae2_source", "arseng:source" -> 8;
            case "neoecoae:mana", "neoecoae:mega_mana", "appbot:mana" -> 9;
            case "neoecoae:omni", "ae2omnicells:omni", "ae2omnicells:omni_cell" -> 10;
            case "neoecoae:complex_omni", "ae2omnicells:complex_omni", "ae2omnicells:complex_omni_cell" -> 11;
            case "neoecoae:quantum_omni", "ae2omnicells:quantum_omni", "ae2omnicells:quantum_omni_cell" -> 12;
            case "neoecoae:spent_nuclear_waste", "ae2omnicells:spent_nuclear_waste", "ae2omnicells:spent_nuclear_waste_cell", "mekanism:spent_nuclear_waste" -> 13;
            case "neoecoae:data", "neoecoae:mega_data", "data_energistics:data", "data_energistics:data_flow", "data_energistics:digitalization", "data_energistics:manifest_binary" -> DATA_SWATCH;
            case "neoecoae:radioactive_chemical" -> RADIOACTIVE_SWATCH;
            case "neoecoae:stress", "neoecoae:mega_stress", "appliedcreate:stress" -> STRESS_SWATCH;
            case "neoecoae:matter", "neoecoae:mega_matter", "apprep:m" -> MATTER_SWATCH;
            default -> FALLBACK_SWATCH;
        };
    }

    private StoragePanelTextures() {}
}
