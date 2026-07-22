package cn.dancingsnow.neoecoae;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean enableEcoCraftingFastPath = true;
    public static boolean enableEcoAggressiveCraftingFastPath = true;
    public static boolean enableEcoProcessingPatternFastPath = true;
    public static int ecoBatchCraftingTickLimit = 256;
    public static int ecoAggressiveCraftingTickLimit = 16384;
    public static int patternUploadPanelHeight;
    private static Configuration configuration;

    public static void synchronizeConfiguration(File configFile) {
        configuration = new Configuration(configFile);

        enableEcoCraftingFastPath = configuration.getBoolean(
            "enableEcoCraftingFastPath",
            Configuration.CATEGORY_GENERAL,
            enableEcoCraftingFastPath,
            "Enable ECO crafting planner inspection and caching before accepted work is queued.");
        enableEcoAggressiveCraftingFastPath = configuration.getBoolean(
            "enableEcoAggressiveCraftingFastPath",
            Configuration.CATEGORY_GENERAL,
            enableEcoAggressiveCraftingFastPath,
            "Enable the large ECO batch path. Disable to retain the verified 256-craft normal batch path.");
        enableEcoProcessingPatternFastPath = configuration.getBoolean(
            "enableEcoProcessingPatternFastPath",
            Configuration.CATEGORY_GENERAL,
            enableEcoProcessingPatternFastPath,
            "Allow runtime-verified, deterministic processing patterns to use the ECO batch path.");
        ecoBatchCraftingTickLimit = configuration.getInt(
            "ecoBatchCraftingTickLimit",
            Configuration.CATEGORY_GENERAL,
            ecoBatchCraftingTickLimit,
            1,
            65536,
            "Maximum verified normal-path crafts an ECO CPU may dispatch per tick.");
        ecoAggressiveCraftingTickLimit = configuration.getInt(
            "ecoAggressiveCraftingTickLimit",
            Configuration.CATEGORY_GENERAL,
            ecoAggressiveCraftingTickLimit,
            1,
            65536,
            "Maximum verified aggressive-path crafts an ECO CPU may dispatch per tick.");
        patternUploadPanelHeight = configuration.getInt(
            "patternUploadPanelHeight",
            Configuration.CATEGORY_GENERAL,
            patternUploadPanelHeight,
            0,
            2,
            "Remembered upload panel height: 0 = short, 1 = medium, 2 = long.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static boolean isEcoAggressiveCraftingFastPathEnabled() {
        return enableEcoCraftingFastPath && enableEcoAggressiveCraftingFastPath;
    }

    public static boolean isEcoProcessingPatternFastPathEnabled() {
        return enableEcoCraftingFastPath && enableEcoProcessingPatternFastPath;
    }

    public static int getEcoCraftingFastPathTickLimit() {
        int configured = isEcoAggressiveCraftingFastPathEnabled() ? ecoAggressiveCraftingTickLimit
            : ecoBatchCraftingTickLimit;
        return Math.max(1, Math.min(65536, configured));
    }

    public static int getPatternUploadPanelHeight() {
        return Math.max(0, Math.min(2, patternUploadPanelHeight));
    }

    public static void setPatternUploadPanelHeight(int mode) {
        int normalized = Math.max(0, Math.min(2, mode));
        if (patternUploadPanelHeight == normalized) return;
        patternUploadPanelHeight = normalized;
        if (configuration != null) {
            configuration.get(Configuration.CATEGORY_GENERAL, "patternUploadPanelHeight", normalized)
                .set(normalized);
            configuration.save();
        }
    }
}
