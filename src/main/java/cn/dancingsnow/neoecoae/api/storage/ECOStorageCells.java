package cn.dancingsnow.neoecoae.api.storage;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;

public class ECOStorageCells {

    private ECOStorageCells() {}

    // Copy-on-write: lookups run for every new-type insert and drive mount, registrations only during startup.
    private static volatile IECOCellHandler[] handlers = new IECOCellHandler[0];

    public static synchronized void register(IECOCellHandler handler) {
        for (IECOCellHandler existing : handlers) if (existing.equals(handler))
            throw new IllegalArgumentException("Tried to register the same handler instance twice.");
        IECOCellHandler[] next = java.util.Arrays.copyOf(handlers, handlers.length + 1);
        next[handlers.length] = java.util.Objects.requireNonNull(handler);
        handlers = next;
    }

    /** Removes a handler registered by {@link #register}; intended for tests and integration teardown. */
    public static synchronized void unregister(IECOCellHandler handler) {
        IECOCellHandler[] current = handlers;
        for (int i = 0; i < current.length; i++) if (current[i].equals(handler)) {
            IECOCellHandler[] next = new IECOCellHandler[current.length - 1];
            System.arraycopy(current, 0, next, 0, i);
            System.arraycopy(current, i + 1, next, i, next.length - i);
            handlers = next;
            return;
        }
    }

    public static boolean isCellHandled(ItemStack stack) {
        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
            return false;
        }
        for (IECOCellHandler handler : handlers) {
            if (handler.isCell(stack)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
            return null;
        }
        for (IECOCellHandler handler : handlers) {
            IECOStorageCell inv = handler.getCellInventory(stack, host);
            if (inv != null) {
                return inv;
            }
        }
        return null;
    }

    public static void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack) || host == null) {
            return;
        }
        for (IECOCellHandler handler : handlers) {
            if (handler.isCell(stack)) {
                handler.releaseCellInventory(stack, host);
                return;
            }
        }
    }

    public static void clearRuntimeState() {
        for (IECOCellHandler handler : handlers) {
            handler.clearRuntimeState();
        }
    }
}
