package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.lang.reflect.Field;
import java.util.List;

import appeng.helpers.DualityInterface;

/** Source scaled-dispatch capacity proof over the pinned native interface's actual pending-send inventory. */
public final class NativeProcessingAccess {

    private static final Field WAITING = field();

    private NativeProcessingAccess() {}

    private static Field field() {
        try {
            Field field = DualityInterface.class.getDeclaredField("waitingToSend");
            if (!List.class.isAssignableFrom(field.getType()))
                throw new IllegalStateException("Native send buffer changed type");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned native interface send-buffer API changed", failure);
        }
    }

    public static boolean isSendBufferEmpty(DualityInterface provider) {
        try {
            List<?> pending = (List<?>) WAITING.get(provider);
            return pending == null || pending.isEmpty();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Native interface pending inputs unavailable", failure);
        }
    }
}
