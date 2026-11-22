package cn.dancingsnow.neoecoae.compat.gtnr.upload;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.Constants;

import cn.dancingsnow.neoecoae.NeoECOAE;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;

public final class CrossRecipePatternUpload {

    private static final String BUFFER_CLASS = "com.xyp.gtneverreset.common.machine.hatch.CrossRecipeMEPatternBuffer";
    private static final String MIRROR_CLASS = "com.xyp.gtneverreset.common.machine.hatch.CrossRecipeMEPatternMirror";
    private static final String JOINT_FACTORY_CLASS = "com.xyp.gtneverreset.common.machine.multiblock.UniversalJointFactory";
    private static final String CONTROLLER_CLASS = "com.xyp.gtneverreset.common.machine.multiMachineBase.CrossRecipeMultiblockBase";
    private static final String PATTERN_MAP_TAG = "gtnrPatternRecipeMap";

    /** Accessors for the buffer's terminal recipe pool, tried in order. */
    private static final String[] TERMINAL_MAP_METHODS = { "getTerminalRecipeMap" };
    private static final String[] TERMINAL_MAP_FIELDS = { "terminalRecipeMap" };

    private static final ClassValue<Accessor> TERMINAL_MAP = new ClassValue<Accessor>() {

        @Override
        protected Accessor computeValue(Class<?> type) {
            return terminalAccessor(type);
        }
    };
    private static final Set<Class<?>> REPORTED = Collections.newSetFromMap(new ConcurrentHashMap<>());

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

    /**
     * The recipe pool selected in the buffer's terminal: {@code null} when it cannot be read, {@code ""} when none is
     * selected, otherwise the pool's unlocalized recipe map name (or the raw value when no loaded map matches it).
     * An unreadable pool must never be treated as "no restriction".
     */
    public static String terminalRecipeMap(Object host) {
        if (host == null) return null;
        Class<?> type = host.getClass();
        Accessor accessor = TERMINAL_MAP.get(type);
        if (accessor == Accessor.NONE) {
            reportUnreadable(type, "no accessor");
            return null;
        }
        Object value;
        try {
            value = accessor.read(host);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
            reportUnreadable(type, failed.toString());
            return null;
        }
        if (value == null) return "";
        if (value instanceof RecipeMap<?>map) return map.unlocalizedName;
        if (value instanceof String id) {
            if (id.isEmpty()) return "";
            RecipeMap<?> map = resolveMap(id);
            return map == null || map.unlocalizedName == null ? id : map.unlocalizedName;
        }
        reportUnreadable(
            type,
            "unsupported value type " + value.getClass()
                .getName());
        return null;
    }

    /** A recipe map by unlocalized name, then by GT's old NEI identifier; null when neither is loaded. */
    public static RecipeMap<?> resolveMap(String id) {
        if (id == null || id.isEmpty()) return null;
        RecipeMap<?> map = RecipeMap.ALL_RECIPE_MAPS.get(id);
        if (map != null) return map;
        try {
            return RecipeMap.getFromOldIdentifier(id);
        } catch (RuntimeException unknown) {
            return null;
        }
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
        if (host == null) return null;
        Method method = findMethod(host.getClass(), methodName);
        if (method == null) return null;
        try {
            return method.invoke(host);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    private static Accessor terminalAccessor(Class<?> type) {
        for (String name : TERMINAL_MAP_METHODS) {
            Method method = findMethod(type, name);
            if (method != null && method.getReturnType() != void.class) return new Accessor(method, null);
        }
        for (String name : TERMINAL_MAP_FIELDS) {
            Field field = findField(type, name);
            if (field != null) return new Accessor(null, field);
        }
        return Accessor.NONE;
    }

    /** A no-argument method declared anywhere in the hierarchy, public or not. */
    private static Method findMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException absent) {
            // Fall through to non-public declarations.
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
        for (Class<?> declaring = type; declaring != null; declaring = declaring.getSuperclass()) {
            try {
                Method method = declaring.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException absent) {
                // Keep walking up.
            } catch (RuntimeException | LinkageError unavailable) {
                return null;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> declaring = type; declaring != null; declaring = declaring.getSuperclass()) {
            try {
                Field field = declaring.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException absent) {
                // Keep walking up.
            } catch (RuntimeException | LinkageError unavailable) {
                return null;
            }
        }
        return null;
    }

    /** Logs once per buffer class, listing members that look like the pool so the accessor names can be fixed. */
    private static void reportUnreadable(Class<?> type, String detail) {
        if (!REPORTED.add(type)) return;
        List<String> candidates = new ArrayList<>();
        for (Class<?> declaring = type; declaring != null
            && declaring != Object.class; declaring = declaring.getSuperclass()) {
            try {
                for (Method method : declaring.getDeclaredMethods())
                    if (method.getParameterTypes().length == 0 && looksLikePool(method.getName())) candidates.add(
                        declaring.getSimpleName() + "#"
                            + method.getName()
                            + "():"
                            + method.getReturnType()
                                .getSimpleName());
                for (Field field : declaring.getDeclaredFields()) if (looksLikePool(field.getName())) candidates.add(
                    declaring.getSimpleName() + "."
                        + field.getName()
                        + ":"
                        + field.getType()
                            .getSimpleName());
            } catch (RuntimeException | LinkageError unavailable) {
                // Diagnostics only.
            }
        }
        NeoECOAE.LOG.warn(
            "GTNR cross-recipe buffer {} terminal recipe map is unreadable ({}); candidates: {}",
            type.getName(),
            detail,
            candidates);
    }

    private static boolean looksLikePool(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("recipemap") || lower.contains("terminal") || lower.contains("pool");
    }

    private static final class Accessor {

        static final Accessor NONE = new Accessor(null, null);

        final Method method;
        final Field field;

        Accessor(Method method, Field field) {
            this.method = method;
            this.field = field;
        }

        Object read(Object host) throws ReflectiveOperationException {
            return method != null ? method.invoke(host) : field.get(host);
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
