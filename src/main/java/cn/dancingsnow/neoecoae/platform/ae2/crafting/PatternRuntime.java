package cn.dancingsnow.neoecoae.platform.ae2.crafting;

/** Generation boundary for frozen pattern contracts and high-version derived caches. */
public final class PatternRuntime {

    private static volatile long generation;

    private PatternRuntime() {}

    public static long reloadGeneration() {
        return generation;
    }

    public static boolean isAvailable() {
        return NativePatternAccess.available();
    }

    public static Class<?> implementationClass(IPatternDetails pattern) {
        Class<?> nativeType = pattern.implementationClass();
        return nativeType == null ? pattern.getClass() : nativeType;
    }

    public static java.util.Optional<cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathKey> buildFastPathKey(
        IPatternDetails details, cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter[] container,
        net.minecraft.world.World world) {
        if (details == null || details.getDefinition() == null) return java.util.Optional.empty();
        return cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathKey
            .of(details.getDefinition(), container, world, generation);
    }

    public static synchronized void onRecipeReloadOrServerReload() {
        generation++;
        cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledStructureCache.clear();
        cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache.clearAllCaches();
        cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry.clear();
    }
}
