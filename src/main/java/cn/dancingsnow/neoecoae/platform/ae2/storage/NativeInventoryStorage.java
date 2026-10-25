package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** An actual native inventory viewed through the upstream key/amount API. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class NativeInventoryStorage implements MEStorage {

    protected final IMEInventory nativeInventory;

    public NativeInventoryStorage(IMEInventory inventory) {
        nativeInventory = java.util.Objects.requireNonNull(inventory);
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || key == null
            || key.getType()
                .nativeChannel() != nativeInventory.getChannel())
            return 0;
        IAEStack remaining = nativeInventory.injectItems(key.toNative(amount), mode, source.nativeSource());
        return amount - (remaining == null ? 0 : remaining.getStackSize());
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || key == null
            || key.getType()
                .nativeChannel() != nativeInventory.getChannel())
            return 0;
        IAEStack extracted = nativeInventory.extractItems(key.toNative(amount), mode, source.nativeSource());
        return extracted == null ? 0 : extracted.getStackSize();
    }

    @Override
    public void getAvailableStacks(KeyCounter output) {
        IItemList listed = nativeInventory.getAvailableItems(
            nativeInventory.getChannel()
                .createList());
        for (Object value : listed) {
            IAEStack stack = (IAEStack) value;
            AEKey key = AEKey.fromNative(stack);
            if (key != null && stack.getStackSize() > 0)
                cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator.add(output, key, stack.getStackSize());
        }
    }

    @Override
    public Component getDescription() {
        return Component.literal("ME storage");
    }
}
