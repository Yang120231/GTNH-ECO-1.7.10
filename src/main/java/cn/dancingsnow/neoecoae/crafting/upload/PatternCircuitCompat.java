package cn.dancingsnow.neoecoae.crafting.upload;

import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.item.ItemStack;

import gregtech.common.items.ItemIntegratedCircuit;

/**
 * Handles the two virtual-circuit representations used by GTNH installations.
 *
 * <p>
 * Programmable Hatches stores the item written by its programming cover inside a
 * {@code reobf.proghatches.item.ItemProgrammingCircuit}. The class is optional, so every
 * access is reflective and the base mod remains loadable without Programmable Hatches.
 * </p>
 */
public final class PatternCircuitCompat {

    private static final String PROGRAMMING_CIRCUIT_CLASS = "reobf.proghatches.item.ItemProgrammingCircuit";
    private static volatile boolean lookedUp;
    private static Method getCircuit;
    private static Class<?> programmingCircuitType;

    private PatternCircuitCompat() {}

    public static ItemStack unwrap(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        if (stack.getItem() instanceof ItemIntegratedCircuit) return stack.copy();
        lookup();
        if (getCircuit == null || programmingCircuitType == null || !programmingCircuitType.isInstance(stack.getItem()))
            return null;
        try {
            Object result = getCircuit.invoke(null, stack);
            if (result instanceof Optional) {
                Object value = ((Optional<?>) result).orElse(null);
                return value instanceof ItemStack ? ((ItemStack) value).copy() : null;
            }
            return result instanceof ItemStack ? ((ItemStack) result).copy() : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isProgrammingCircuit(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        lookup();
        return programmingCircuitType != null && programmingCircuitType.isInstance(stack.getItem());
    }

    public static ItemStack normalizeForRecipe(ItemStack stack) {
        ItemStack unwrapped = unwrap(stack);
        if (unwrapped != null) return unwrapped;
        return isProgrammingCircuit(stack) ? null : stack == null ? null : stack.copy();
    }

    public static boolean isVirtualCircuit(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (stack.getItem() instanceof ItemIntegratedCircuit) {
            int damage = stack.getItemDamage();
            return damage >= 0 && damage <= ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER;
        }
        return unwrap(stack) != null;
    }

    public static boolean same(ItemStack left, ItemStack right) {
        ItemStack normalizedLeft = unwrap(left);
        ItemStack normalizedRight = unwrap(right);
        if (normalizedLeft == null) normalizedLeft = left;
        if (normalizedRight == null) normalizedRight = right;
        if (normalizedLeft == null || normalizedRight == null) return false;
        if (normalizedLeft.getItem() instanceof ItemIntegratedCircuit
            && normalizedRight.getItem() instanceof ItemIntegratedCircuit) {
            return (normalizedLeft.getItemDamage() & 0xFF) == (normalizedRight.getItemDamage() & 0xFF);
        }
        return normalizedLeft.isItemEqual(normalizedRight)
            && ItemStack.areItemStackTagsEqual(normalizedLeft, normalizedRight);
    }

    private static void lookup() {
        if (lookedUp) return;
        synchronized (PatternCircuitCompat.class) {
            if (lookedUp) return;
            try {
                programmingCircuitType = Class.forName(PROGRAMMING_CIRCUIT_CLASS);
                getCircuit = programmingCircuitType.getMethod("getCircuit", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                programmingCircuitType = null;
                getCircuit = null;
            }
            lookedUp = true;
        }
    }
}
