package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/** Applies the imported listing's nonnegative, saturating projection at GTNH's mutable list boundary. */
public final class NativeStackAmounts {

    // Native NetworkItemList retains stack identity through its filtering and subnet deduplication.
    // Keep exact amounts on those same entries, and aggregate only entries AE2 actually publishes.
    private static final ThreadLocal<java.util.IdentityHashMap<IAEStack, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount>> EXACT = new ThreadLocal<>();

    public static void beginExact() {
        if (EXACT.get() != null) throw new IllegalStateException("Nested native exact listing");
        EXACT.set(new java.util.IdentityHashMap<>());
    }

    public static void endExact() {
        EXACT.remove();
    }

    public static cn.dancingsnow.neoecoae.crafting.amount.ExactAmount exact(IAEStack stack) {
        var values = EXACT.get();
        var amount = values == null ? null : values.get(stack);
        return amount == null
            ? cn.dancingsnow.neoecoae.crafting.amount.ExactAmount
                .finite(java.math.BigInteger.valueOf(Math.max(0L, stack.getStackSize())))
            : amount;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void capture(appeng.api.storage.IMEInventory inventory, IItemList listed) {
        var values = EXACT.get();
        if (values == null || listed instanceof appeng.util.item.NetworkItemList) return;
        var amounts = cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector
            .sourceAmounts(new NativeInventoryStorage(inventory));
        if (amounts.isEmpty()) return;
        for (Object value : listed) {
            IAEStack stack = (IAEStack) value;
            var amount = amounts.get(cn.dancingsnow.neoecoae.platform.ae2.key.AEKey.fromNative(stack));
            if (amount != null && (amount.infinite() || amount.value()
                .bitLength() > 63)) values.put(stack, amount);
        }
    }

    private NativeStackAmounts() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void add(IItemList out, IAEStack incoming) {
        if (incoming == null) return;
        IAEStack existing = out.findPrecise(incoming);
        long previous = existing == null ? 0 : Math.max(0L, existing.getStackSize());
        if (existing != null && existing.getStackSize() < 0) existing.setStackSize(0);
        long offered = incoming.getStackSize();
        var values = EXACT.get();
        cn.dancingsnow.neoecoae.crafting.amount.ExactAmount exact = null;
        if (values != null && (values.containsKey(incoming) || existing != null && values.containsKey(existing)
            || offered > Long.MAX_VALUE - previous)) {
            exact = existing == null ? exact(incoming) : exact(existing).add(exact(incoming));
        }
        long amount = Math.min(Math.max(0L, offered), Long.MAX_VALUE - previous);
        // Let AE2 merge crafting/requestable flags using its normal behavior, while bounding stored stock.
        // rv3 lists copy a stack they keep, so only a clamped amount needs a private copy.
        out.add(
            amount == offered ? incoming
                : incoming.copy()
                    .setStackSize(amount));
        if (exact != null) values.put(out.findPrecise(incoming), exact);
    }
}
