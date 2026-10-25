package cn.dancingsnow.neoecoae.api.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;

public class ECOStorageCells {

    private ECOStorageCells() {}

    private static final List<IECOCellHandler> handlers = new ArrayList<>();

    public static void register(IECOCellHandler handler) {
        if (handlers.contains(handler)) {
            throw new IllegalArgumentException("Tried to register the same handler instance twice.");
        }
        handlers.add(handler);
    }

    public static synchronized boolean isCellHandled(ItemStack stack) {
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
    public static synchronized IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
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

    public static synchronized void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {
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

    public static synchronized void clearRuntimeState() {
        for (IECOCellHandler handler : handlers) {
            handler.clearRuntimeState();
        }
    }
}
