package cn.dancingsnow.neoecoae.compat.proghatches.upload;

import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.item.ItemStack;

/**
 * Programmable Hatches support, accessed reflectively so the mod stays optional. Its programming circuit wraps the
 * circuit a cover writes; an empty wrapper is a reset marker, not an ingredient.
 */
public final class ProgrammableHatchUpload {

    private static final String HATCH_CLASS = "reobf.proghatches.gt.metatileentity.PatternDualInputHatch";
    private static final String COVER_CLASS = "reobf.proghatches.gt.cover.ProgrammingCover";
    private static final String CIRCUIT_CLASS = "reobf.proghatches.item.ItemProgrammingCircuit";
    private static volatile boolean lookedUp;
    private static Class<?> circuitType;
    private static Method getCircuit;

    private ProgrammableHatchUpload() {}

    public static boolean isHatch(Object host) {
        return extendsClass(host, HATCH_CLASS);
    }

    public static boolean isCover(Object cover) {
        return extendsClass(cover, COVER_CLASS);
    }

    public static boolean isProgrammingCircuit(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        lookup();
        return circuitType != null && circuitType.isInstance(stack.getItem());
    }

    /** The circuit carried by a programming circuit, or null (also for an empty reset marker). */
    public static ItemStack unwrap(ItemStack stack) {
        if (!isProgrammingCircuit(stack) || getCircuit == null) return null;
        try {
            Object result = getCircuit.invoke(null, stack);
            if (result instanceof Optional) result = ((Optional<?>) result).orElse(null);
            return result instanceof ItemStack ? ((ItemStack) result).copy() : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean extendsClass(Object value, String name) {
        for (Class<?> type = value == null ? null : value.getClass(); type != null; type = type.getSuperclass())
            if (name.equals(type.getName())) return true;
        return false;
    }

    private static void lookup() {
        if (lookedUp) return;
        synchronized (ProgrammableHatchUpload.class) {
            if (lookedUp) return;
            try {
                circuitType = Class.forName(CIRCUIT_CLASS);
                getCircuit = circuitType.getMethod("getCircuit", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError absent) {
                circuitType = null;
                getCircuit = null;
            }
            lookedUp = true;
        }
    }
}
