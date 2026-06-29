package cn.dancingsnow.neoecoae.crafting.fastpath;

public final class ECOFastPathConfig {

    public static final boolean PLANNER_HOOK_ENABLED = false;
    public static final int PATTERN_CACHE_SIZE = 512;
    public static final int NEGATIVE_CACHE_SIZE = 256;
    public static final int MAX_PATTERN_INPUTS = 9;
    public static final int MAX_PATTERN_OUTPUTS = 3;

    private ECOFastPathConfig() {}

    public static boolean isPlannerHookEnabled() {
        return PLANNER_HOOK_ENABLED;
    }

    public static int patternCacheSize() {
        return PATTERN_CACHE_SIZE;
    }

    public static int negativeCacheSize() {
        return NEGATIVE_CACHE_SIZE;
    }
}
