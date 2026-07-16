package cn.dancingsnow.neoecoae;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void aggressiveFastPathUsesIndependentOptInLimit() {
        boolean previousFastPath = Config.enableEcoCraftingFastPath;
        boolean previousAggressive = Config.enableEcoAggressiveCraftingFastPath;
        int previousNormalLimit = Config.ecoBatchCraftingTickLimit;
        int previousAggressiveLimit = Config.ecoAggressiveCraftingTickLimit;
        try {
            Config.enableEcoCraftingFastPath = true;
            Config.enableEcoAggressiveCraftingFastPath = true;
            Config.ecoBatchCraftingTickLimit = 256;
            Config.ecoAggressiveCraftingTickLimit = 4096;
            assertEquals(4096, Config.getEcoCraftingFastPathTickLimit());

            Config.enableEcoAggressiveCraftingFastPath = false;
            assertEquals(256, Config.getEcoCraftingFastPathTickLimit());
        } finally {
            Config.enableEcoCraftingFastPath = previousFastPath;
            Config.enableEcoAggressiveCraftingFastPath = previousAggressive;
            Config.ecoBatchCraftingTickLimit = previousNormalLimit;
            Config.ecoAggressiveCraftingTickLimit = previousAggressiveLimit;
        }
    }
}
