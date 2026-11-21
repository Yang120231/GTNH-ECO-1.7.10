package cn.dancingsnow.neoecoae.compat.gtnr.upload;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.Constants;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;

public final class CrossRecipePatternUpload {

    private static final String BUFFER_CLASS = "com.xyp.gtneverreset.common.machine.hatch.CrossRecipeMEPatternBuffer";
    private static final String MIRROR_CLASS = "com.xyp.gtneverreset.common.machine.hatch.CrossRecipeMEPatternMirror";
    private static final String JOINT_FACTORY_CLASS = "com.xyp.gtneverreset.common.machine.multiblock.UniversalJointFactory";
    private static final String CONTROLLER_CLASS = "com.xyp.gtneverreset.common.machine.multiMachineBase.CrossRecipeMultiblockBase";
    private static final String PATTERN_MAP_TAG = "gtnrPatternRecipeMap";

    public enum MapSupport {
        UNAVAILABLE,
        UNVERIFIED,
        VERIFIED
    }

    private CrossRecipePatternUpload() {}

    public static String patternRecipeMap(ItemStack pattern) {
        var tag = pattern == null ? null : pattern.getTagCompound();
        if (tag == null || !tag.hasKey(PATTERN_MAP_TAG)) return null;
        return tag.hasKey(PATTERN_MAP_TAG, Constants.NBT.TAG_STRING) ? tag.getString(PATTERN_MAP_TAG) : "";
    }

    public static int storedCircuit(ItemStack pattern) {
        if (CircuitCodec.STORED_CIRCUIT == null) return -1;
        try {
            return (int) CircuitCodec.STORED_CIRCUIT.invoke(null, pattern);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return -1;
        }
    }

    public static boolean isBuffer(Object host) {
        return isType(host, BUFFER_CLASS);
    }

    public static Object mirrorMaster(Object mirror) {
        Object master = isType(mirror, MIRROR_CLASS) ? invoke(mirror, "getMaster") : null;
        return isBuffer(master) ? master : null;
    }

    private static boolean isType(Object host, String name) {
        for (Class<?> type = host == null ? null : host.getClass(); type != null; type = type.getSuperclass())
            if (name.equals(type.getName())) return true;
        return false;
    }

    public static String terminalRecipeMap(Object host) {
        Object selected = invoke(host, "getTerminalRecipeMap");
        return selected instanceof String map ? map : "";
    }

    public static MapSupport mapSupport(MTEMultiBlockBase controller, RecipeMap<?> current, RecipeMap<?> requested) {
        boolean nativeCrossRecipe = isType(controller, CONTROLLER_CLASS) || isType(controller, JOINT_FACTORY_CLASS);
        if (!nativeCrossRecipe && current == requested) return MapSupport.VERIFIED;
        if (!controller.getAvailableRecipeMaps()
            .contains(requested)) return MapSupport.UNAVAILABLE;
        if (nativeCrossRecipe) return MapSupport.VERIFIED;
        return controller.supportsMachineModeSwitch() && !controller.isRecipeLockingEnabled() ? MapSupport.UNVERIFIED
            : MapSupport.UNAVAILABLE;
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

    private static final class CircuitCodec {

        private static final Method STORED_CIRCUIT = findStoredCircuit();

        private static Method findStoredCircuit() {
            try {
                return Class.forName("com.xyp.gtneverreset.common.me.CircuitPatternCodec")
                    .getMethod("storedCircuit", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
