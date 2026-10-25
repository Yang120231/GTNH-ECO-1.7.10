package cn.dancingsnow.neoecoae.platform.ae2.storage;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOCreativeExtractionFilter;
import cn.dancingsnow.neoecoae.impl.storage.transfer.StorageBusTransferFilter;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Native mount identity and call conversion for upstream transfer-scoped filters. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class NativeTransferFilters {

    private static final Set<IMEInventory> BUSES = Collections
        .synchronizedSet(Collections.newSetFromMap(new WeakHashMap<IMEInventory, Boolean>()));

    private NativeTransferFilters() {}

    public static void storageBus(IMEInventory inventory) {
        if (inventory != null) BUSES.add(inventory);
    }

    public static boolean excluded(IMEInventory inventory) {
        return BUSES.contains(inventory) && StorageBusTransferFilter.excludes(new BusStorage(inventory));
    }

    public static IAEStack extract(IMEInventory inventory, IAEStack request, Actionable mode, BaseActionSource source) {
        if (excluded(inventory)) return null;
        AEKey key = AEKey.fromNative(request);
        if (key == null) return (IAEStack) inventory.extractItems(request, mode, source);
        MEStorage view = inventory instanceof DelegatingStorage ? ((DelegatingStorage) inventory).neoecoae$getDelegate()
            : new NativeInventoryStorage(inventory);
        IAEStack[] actual = new IAEStack[1];
        long extracted = ECOCreativeExtractionFilter.extractSource(view, key, mode, () -> {
            actual[0] = (IAEStack) inventory.extractItems(request, mode, source);
            return actual[0] == null ? 0 : actual[0].getStackSize();
        });
        return extracted > 0 ? actual[0] : null;
    }

    private static final class BusStorage extends NativeInventoryStorage
        implements StorageBusTransferFilter.StorageBusInventory {

        BusStorage(IMEInventory inventory) {
            super(inventory);
        }
    }
}
