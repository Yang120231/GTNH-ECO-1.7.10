package cn.dancingsnow.neoecoae.crafting.fastpath;

import cn.dancingsnow.neoecoae.Config;

public final class ECOFastPathConfig {

    public static final int PATTERN_CACHE_SIZE = 512;
    public static final int NEGATIVE_CACHE_SIZE = 256;
    public static final int MAX_PATTERN_INPUTS = 9;
    public static final int MAX_PATTERN_OUTPUTS = 3;
    public static final int MAX_BATCH_SIZE = 65536;

    private ECOFastPathConfig() {}

    public static boolean isPlannerHookEnabled() {
        return Config.enableEcoCraftingFastPath;
    }

    public static int patternCacheSize() {
        return PATTERN_CACHE_SIZE;
    }

    public static int negativeCacheSize() {
        return NEGATIVE_CACHE_SIZE;
    }

    public static int batchTickLimit() {
        return Config.getEcoCraftingFastPathTickLimit();
    }

    public static boolean isAggressiveBatchEnabled() {
        return Config.isEcoAggressiveCraftingFastPathEnabled();
    }
}
