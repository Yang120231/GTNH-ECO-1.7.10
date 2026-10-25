package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/** Applies the imported listing's nonnegative, saturating projection at GTNH's mutable list boundary. */
public final class NativeStackAmounts {

    private NativeStackAmounts() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void add(IItemList out, IAEStack incoming) {
        if (incoming == null) return;
        IAEStack existing = out.findPrecise(incoming);
        long previous = existing == null ? 0 : Math.max(0L, existing.getStackSize());
        if (existing != null && existing.getStackSize() < 0) existing.setStackSize(0);
        long amount = Math.min(Math.max(0L, incoming.getStackSize()), Long.MAX_VALUE - previous);
        // Let AE2 merge crafting/requestable flags using its normal behavior, while bounding stored stock.
        out.add(
            incoming.copy()
                .setStackSize(amount));
    }
}
