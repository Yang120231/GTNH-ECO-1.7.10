package cn.dancingsnow.neoecoae;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean enableEcoCraftingFastPath = true;
    public static boolean enableEcoAggressiveCraftingFastPath = true;
    public static int ecoBatchCraftingTickLimit = 256;
    public static int ecoAggressiveCraftingTickLimit = 16384;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

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

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static boolean isEcoAggressiveCraftingFastPathEnabled() {
        return enableEcoCraftingFastPath && enableEcoAggressiveCraftingFastPath;
    }

    public static int getEcoCraftingFastPathTickLimit() {
        int configured = isEcoAggressiveCraftingFastPathEnabled() ? ecoAggressiveCraftingTickLimit
            : ecoBatchCraftingTickLimit;
        return Math.max(1, Math.min(65536, configured));
    }
}
