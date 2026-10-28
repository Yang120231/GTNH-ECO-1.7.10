package cn.dancingsnow.neoecoae.platform.ae2.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** Both native monitors belong to the same actual grid; no secondary inventory or ledger. */
public final class NativeNetworkStorage implements MEStorage {

    private final IStorageGrid grid;

    public NativeNetworkStorage(IStorageGrid grid) {
        this.grid = grid;
    }

    private MEStorage channel(AEKey key) {
        return new NativeInventoryStorage(
            key.getType() == cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType.items() ? grid.getItemInventory()
                : grid.getFluidInventory());
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return key == null ? 0 : channel(key).insert(key, amount, mode, source);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return key == null ? 0 : channel(key).extract(key, amount, mode, source);
    }

    @Override
    public long getAvailableAmount(AEKey key) {
        return key == null ? 0 : channel(key).getAvailableAmount(key);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        new NativeInventoryStorage(grid.getItemInventory()).getAvailableStacks(out);
        new NativeInventoryStorage(grid.getFluidInventory()).getAvailableStacks(out);
    }

    @Override
    public Component getDescription() {
        return Component.literal("ME network");
    }
}
