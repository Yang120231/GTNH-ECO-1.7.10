package cn.dancingsnow.neoecoae.platform.ae2.crafting;

/** Generation boundary for frozen pattern contracts and high-version derived caches. */
public final class PatternRuntime {

    private static volatile long generation;

    private PatternRuntime() {}

    public static long reloadGeneration() {
        return generation;
    }

    public static synchronized void onRecipeReloadOrServerReload() {
        generation++;
        cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry.clear();
    }
}
