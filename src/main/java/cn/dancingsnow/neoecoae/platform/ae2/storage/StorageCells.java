package cn.dancingsnow.neoecoae.platform.ae2.storage;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

public final class StorageCells {

    private StorageCells() {}

    public static appeng.api.storage.ICellHandler getHandler(ItemStack stack) {
        return stack == null ? null
            : AEApi.instance()
                .registries()
                .cell()
                .getHandler(stack);
    }

    /** Only the exact built-in stack-owned native inventory is safe to cache by immutable key. */
    public static boolean isStableNativeInventory(ItemStack stack, StorageCell inventory) {
        return inventory instanceof ForeignCell
            && ((ForeignCell) inventory).inventory instanceof appeng.api.storage.ICellInventoryHandler
            && ((appeng.api.storage.ICellInventoryHandler) ((ForeignCell) inventory).inventory).getCellInv()
                .getClass() == appeng.me.storage.ItemCellInventory.class
            && stack.getItem()
                .getClass() == appeng.items.storage.ItemBasicStorageCell.class;
    }

    public static StorageCell getCellInventory(ItemStack stack, ISaveProvider host) {
        StorageCell own = ECOStorageCells.getCellInventory(stack, host);
        if (own != null) return own;
        for (StorageChannel channel : StorageChannel.values()) {
            IMEInventoryHandler inventory = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(stack, host == null ? null : changed -> host.saveChanges(), channel);
            if (inventory != null) return new ForeignCell(stack, inventory, channel, host);
        }
        return null;
    }

    private static final class ForeignCell implements StorageCell {

        private final ItemStack stack;
        private final IMEInventoryHandler inventory;
        private final StorageChannel channel;
        private final ISaveProvider host;

        ForeignCell(ItemStack stack, IMEInventoryHandler inventory, StorageChannel channel, ISaveProvider host) {
            this.stack = stack;
            this.inventory = inventory;
            this.channel = channel;
            this.host = host;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (key == null || amount <= 0
                || key.getType()
                    .nativeChannel() != channel)
                return 0;
            IAEStack remaining = inventory.injectItems(key.toNative(amount), mode, source.nativeSource());
            return amount - (remaining == null ? 0 : remaining.getStackSize());
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (key == null || amount <= 0
                || key.getType()
                    .nativeChannel() != channel)
                return 0;
            IAEStack result = inventory.extractItems(key.toNative(amount), mode, source.nativeSource());
            return result == null ? 0 : result.getStackSize();
        }

        @Override
        public void getAvailableStacks(KeyCounter output) {
            IItemList available = inventory.getAvailableItems(channel.createList());
            for (Object item : available) {
                IAEStack<?> nativeStack = (IAEStack<?>) item;
                AEKey key = AEKey.fromNative(nativeStack);
                if (key != null) output.add(key, nativeStack.getStackSize());
            }
        }

        @Override
        public boolean canFitInsideCell() {
            return getAvailableStacks().isEmpty();
        }

        @Override
        public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
            return key != null && key.getType()
                .nativeChannel() == channel && inventory.isPrioritized(key.toNative(1));
        }

        @Override
        public void persist() {
            // Native CellInventory writes its item NBT during every mutation.
            if (host != null) host.saveChanges();
        }

        @Override
        public double getIdleDrain() {
            return AEApi.instance()
                .registries()
                .cell()
                .getHandler(stack)
                .cellIdleDrain(stack, inventory);
        }

        @Override
        public CellState getStatus() {
            int status = AEApi.instance()
                .registries()
                .cell()
                .getHandler(stack)
                .getStatusForCell(stack, inventory);
            switch (status) {
                case 1:
                    return CellState.EMPTY;
                case 2:
                    return CellState.NOT_EMPTY;
                case 3:
                    return CellState.TYPES_FULL;
                case 4:
                    return CellState.FULL;
                default:
                    return CellState.ABSENT;
            }
        }

        @Override
        public Component getDescription() {
            return Component.literal(stack.getDisplayName());
        }
    }
}
