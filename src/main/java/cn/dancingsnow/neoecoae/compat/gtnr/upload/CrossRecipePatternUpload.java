package cn.dancingsnow.neoecoae.compat.gtnr.upload;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.Constants;

import gregtech.api.recipe.RecipeMap;

public final class CrossRecipePatternUpload {

    private static final String BUFFER_CLASS = "com.xyp.gtneverreset.common.machine.hatch.CrossRecipeMEPatternBuffer";
    private static final String PATTERN_MAP_TAG = "gtnrPatternRecipeMap";

    private CrossRecipePatternUpload() {}

    public static String patternRecipeMap(ItemStack pattern) {
        if (pattern == null || !pattern.hasTagCompound()
            || !pattern.getTagCompound()
                .hasKey(PATTERN_MAP_TAG, Constants.NBT.TAG_STRING))
            return null;
        return pattern.getTagCompound()
            .getString(PATTERN_MAP_TAG);
    }

    public static boolean isBuffer(Object host) {
        for (Class<?> type = host == null ? null : host.getClass(); type != null; type = type.getSuperclass())
            if (BUFFER_CLASS.equals(type.getName())) return true;
        return false;
    }

    public static boolean selectsSupportedMap(Object host, String recipeMap) {
        if (!isBuffer(host) || recipeMap == null || recipeMap.isEmpty()) return false;
        Object selected = invoke(host, "getTerminalRecipeMap");
        if (!recipeMap.equals(selected)) return false;
        Object available = invoke(host, "getControllerRecipeMaps");
        if (!(available instanceof Iterable<?>maps)) return false;
        for (Object map : maps) if (recipeMap.equals(map)) return true;
        return false;
    }

    public static boolean controllerSupportsMap(Object controller, String recipeMap) {
        Object available = invoke(controller, "getAvailableRecipeMaps");
        if (!(available instanceof Iterable<?>maps)) return false;
        for (Object map : maps)
            if (map instanceof RecipeMap<?>recipe && recipeMap.equals(recipe.unlocalizedName)) return true;
        return false;
    }

    private static Object invoke(Object host, String methodName) {
        try {
            Method method = host.getClass()
                .getMethod(methodName);
            return method.invoke(host);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}
