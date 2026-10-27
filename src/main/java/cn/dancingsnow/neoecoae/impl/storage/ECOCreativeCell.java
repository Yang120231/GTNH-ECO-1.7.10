package cn.dancingsnow.neoecoae.impl.storage;

import java.util.Set;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.items.contents.CellConfig;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.CellState;
import cn.dancingsnow.neoecoae.platform.ae2.storage.ISaveProvider;
import cn.dancingsnow.neoecoae.platform.ae2.storage.NativeCreativeCellHandler;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageCell;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;

/** ECO-only view of an AE2 creative cell; AE2 remains responsible for all storage operations. */
public final class ECOCreativeCell implements IECOStorageCell {

    private static final ECOCellType TYPE = new ECOCellType(
        Component.translatable("item.appliedenergistics2.ItemCreativeStorageCell.name"), 1, false);
    private final StorageCell delegate;
    private final Set<AEKey> configured;
    private final int configSlots;

    ECOCreativeCell(StorageCell delegate, int configSlots) {
        this.delegate = delegate;
        this.configured = delegate.getAvailableStacks()
            .keySet();
        this.configSlots = configSlots;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        // Multiple creative cells and existing finite stacks must not wrap the aggregate negative.
        for (AEKey key : configured) out.set(key, Long.MAX_VALUE);
    }

    /** Internal crafting capability, deliberately separate from terminal quantity reporting. */
    public Set<AEKey> configuredKeys() {
        return configured;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public CellState getStatus() {
        return delegate.getStatus();
    }

    @Override
    public double getIdleDrain() {
        return delegate.getIdleDrain();
    }

    @Override
    public boolean canFitInsideCell() {
        return delegate.canFitInsideCell();
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void persist() {
        delegate.persist();
    }

    @Override
    public StorageTierDescriptor getTier() {
        // L9 is the visual level only; creative cells work with every ECO controller tier.
        return StorageTiers.L4;
    }

    @Override
    public ECOCellType getCellType() {
        return TYPE;
    }

    @Override
    public long getStoredItemTypes() {
        return configured.size();
    }

    @Override
    public long getTotalItemTypes() {
        return configSlots;
    }

    @Override
    public boolean isInfiniteStorageEligible() {
        // Generated resources must never become persisted contents in a migration.
        return false;
    }

    @Override
    public long getUsedBytes() {
        return 0;
    }

    @Override
    public long getTotalBytes() {
        return 0;
    }

    public enum Handler implements IECOCellHandler {

        INSTANCE;

        @Override
        public boolean isCell(ItemStack stack) {
            return NativeCreativeCellHandler.INSTANCE.isCell(stack);
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
            StorageCell delegate = NativeCreativeCellHandler.INSTANCE.getCellInventory(stack, host);
            return delegate == null ? null : new ECOCreativeCell(delegate, new CellConfig(stack).getSizeInventory());
        }
    }
}
