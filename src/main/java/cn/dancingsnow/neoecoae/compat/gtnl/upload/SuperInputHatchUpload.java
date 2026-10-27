package cn.dancingsnow.neoecoae.compat.gtnl.upload;

import java.lang.reflect.Field;

/**
 * GT-Not-Leisure's super crafting input hatch reports only occupied rows to the interface terminal, and its inventory
 * continues with circuit and manual slots. Only the declared pattern capacity is writable.
 */
public final class SuperInputHatchUpload {

    private static final String HATCH_CLASS = "com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME";

    private SuperInputHatchUpload() {}

    public static boolean isHatch(Object host) {
        for (Class<?> type = host == null ? null : host.getClass(); type != null; type = type.getSuperclass())
            if (HATCH_CLASS.equals(type.getName())) return true;
        return false;
    }

    /** Physical pattern slots, or -1 when the installed version does not expose its capacity. */
    public static int patternSlots(Object host) {
        for (Class<?> type = host.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("MAX_PATTERN_COUNT");
                field.setAccessible(true);
                Object value = field.get(java.lang.reflect.Modifier.isStatic(field.getModifiers()) ? null : host);
                if (value instanceof Integer && (Integer) value > 0) return (Integer) value;
                return -1;
            } catch (NoSuchFieldException inherited) {
                // Keep walking towards the declaring class.
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return -1;
            }
        }
        return -1;
    }
}
