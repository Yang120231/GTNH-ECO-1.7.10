package cn.dancingsnow.neoecoae.gui.storage;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.platform.ae2.storage.CellState;
import cn.dancingsnow.neoecoae.platform.inventory.IItemHandlerModifiable;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public final class StorageHostUI {

    public static final int ROOT_WIDTH = 253;

    public static final int ROOT_HEIGHT = 231;

    @com.github.bsideup.jabel.Desugar
    public record StorageTypeLine(ECOCellType type, int registryIndex, Supplier<Component> displayName,
        BooleanSupplier visible, LongSupplier usedTypes, LongSupplier totalTypes, LongSupplier usedBytes,
        LongSupplier totalBytes, Supplier<String> infiniteBytesText, @Nullable ResourceLocation resourceId,
        Supplier<BigInteger> exactUsedBytes) {

        public StorageTypeLine(ECOCellType type, int registryIndex, Supplier<Component> displayName,
            BooleanSupplier visible, LongSupplier usedTypes, LongSupplier totalTypes, LongSupplier usedBytes,
            LongSupplier totalBytes, Supplier<String> infiniteBytesText) {
            this(
                type,
                registryIndex,
                displayName,
                visible,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                infiniteBytesText,
                null,
                () -> BigInteger.valueOf(Math.max(0, usedBytes.getAsLong())));
        }

        public StorageTypeLine(ECOCellType type, int registryIndex, LongSupplier usedTypes, LongSupplier totalTypes,
            LongSupplier usedBytes, LongSupplier totalBytes, Supplier<String> infiniteBytesText) {
            this(
                type,
                registryIndex,
                type::desc,
                type::visible,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                infiniteBytesText);
        }
    }

    @com.github.bsideup.jabel.Desugar
    public record Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed, LongSupplier storedEnergy,
        LongSupplier maxEnergy, LongSupplier energyConsumePerTick, Supplier<String> totalUsedBytesText,
        Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes, BooleanSupplier infiniteStorage,
        BooleanSupplier migratingToInfinite, BooleanSupplier canExtractInfiniteComponents, Supplier<String> domainId,
        IntSupplier missingMembers, IItemHandlerModifiable infiniteComponentInventory, @Nullable EntityPlayer player,
        CellExtractor cellExtractor, CellInserter cellInserter, Supplier<ItemStack> hostItem,
        IntSupplier migrationProgress, Supplier<String> migrationBlockedReason) {

        public Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed, LongSupplier storedEnergy,
            LongSupplier maxEnergy, LongSupplier energyConsumePerTick, Supplier<String> totalUsedBytesText,
            Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes, BooleanSupplier infiniteStorage,
            BooleanSupplier migratingToInfinite, BooleanSupplier canExtractInfiniteComponents,
            Supplier<String> domainId, IntSupplier missingMembers, IItemHandlerModifiable infiniteComponentInventory,
            @Nullable EntityPlayer player, CellExtractor cellExtractor, CellInserter cellInserter,
            Supplier<ItemStack> hostItem) {
            this(
                title,
                tier,
                formed,
                storedEnergy,
                maxEnergy,
                energyConsumePerTick,
                totalUsedBytesText,
                cellEntries,
                storageTypes,
                infiniteStorage,
                migratingToInfinite,
                canExtractInfiniteComponents,
                domainId,
                missingMembers,
                infiniteComponentInventory,
                player,
                cellExtractor,
                cellInserter,
                hostItem,
                () -> 0,
                () -> "");
        }

        public Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed, LongSupplier storedEnergy,
            LongSupplier maxEnergy, LongSupplier energyConsumePerTick, Supplier<String> totalUsedBytesText,
            Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes, BooleanSupplier infiniteStorage,
            BooleanSupplier migratingToInfinite, BooleanSupplier canExtractInfiniteComponents,
            Supplier<String> domainId, IntSupplier missingMembers, IItemHandlerModifiable infiniteComponentInventory,
            @Nullable EntityPlayer player, CellExtractor cellExtractor, CellInserter cellInserter) {
            this(
                title,
                tier,
                formed,
                storedEnergy,
                maxEnergy,
                energyConsumePerTick,
                totalUsedBytesText,
                cellEntries,
                storageTypes,
                infiniteStorage,
                migratingToInfinite,
                canExtractInfiniteComponents,
                domainId,
                missingMembers,
                infiniteComponentInventory,
                player,
                cellExtractor,
                cellInserter,
                () -> null);
        }

        public Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed, LongSupplier storedEnergy,
            LongSupplier maxEnergy, LongSupplier energyConsumePerTick, Supplier<String> totalUsedBytesText,
            Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes, BooleanSupplier infiniteStorage,
            BooleanSupplier migratingToInfinite, BooleanSupplier canExtractInfiniteComponents,
            Supplier<String> domainId, IntSupplier missingMembers, IItemHandlerModifiable infiniteComponentInventory,
            @Nullable EntityPlayer player, CellExtractor cellExtractor) {
            this(
                title,
                tier,
                formed,
                storedEnergy,
                maxEnergy,
                energyConsumePerTick,
                totalUsedBytesText,
                cellEntries,
                storageTypes,
                infiniteStorage,
                migratingToInfinite,
                canExtractInfiniteComponents,
                domainId,
                missingMembers,
                infiniteComponentInventory,
                player,
                cellExtractor,
                (position, expected) -> false);
        }

        public Config(Supplier<Component> title, IntSupplier tier, BooleanSupplier formed, LongSupplier storedEnergy,
            LongSupplier maxEnergy, LongSupplier energyConsumePerTick, Supplier<String> totalUsedBytesText,
            Supplier<List<CellEntry>> cellEntries, List<StorageTypeLine> storageTypes, BooleanSupplier infiniteStorage,
            BooleanSupplier migratingToInfinite, BooleanSupplier canExtractInfiniteComponents,
            Supplier<String> domainId, IntSupplier missingMembers, IItemHandlerModifiable infiniteComponentInventory) {
            this(
                title,
                tier,
                formed,
                storedEnergy,
                maxEnergy,
                energyConsumePerTick,
                totalUsedBytesText,
                cellEntries,
                storageTypes,
                infiniteStorage,
                migratingToInfinite,
                canExtractInfiniteComponents,
                domainId,
                missingMembers,
                infiniteComponentInventory,
                null,
                (position, expected, shift) -> false);
        }

        public HolderLookup.Provider registries() {
            return player == null ? RegistryAccess.EMPTY
                : cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY;
        }
    }

    @FunctionalInterface
    public interface CellExtractor {

        boolean extract(BlockPos position, UUID expected, boolean toInventory);
    }

    @FunctionalInterface
    public interface CellInserter {

        boolean insert(BlockPos position, ItemStack expected);
    }

    @com.github.bsideup.jabel.Desugar
    public record CellEntry(int typeId, int tier, int kind, long usedTypes, long totalTypes, long usedBytes,
        long totalBytes, boolean infinite, CellState state, boolean online, @Nullable BlockPos position,
        ItemStack stack, @Nullable Component extractionBlockReason, @Nullable UUID interactionId,
        @Nullable Component panelTierName) {

        public static final CellEntry EMPTY = new CellEntry(-1, 0, 0, 0, 0, 0, 0, false, CellState.ABSENT, false);

        public CellEntry(int typeId, int tier, int kind, long usedTypes, long totalTypes, long usedBytes,
            long totalBytes, boolean infinite, CellState state, boolean online, @Nullable BlockPos position,
            ItemStack stack, @Nullable Component extractionBlockReason, @Nullable UUID interactionId) {
            this(
                typeId,
                tier,
                kind,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                infinite,
                state,
                online,
                position,
                stack,
                extractionBlockReason,
                interactionId,
                null);
        }

        public CellEntry(int typeId, int tier, int kind, long usedTypes, long totalTypes, long usedBytes,
            long totalBytes, boolean infinite, CellState state, boolean online) {
            this(
                typeId,
                tier,
                kind,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                infinite,
                state,
                online,
                null,
                null,
                null,
                null);
        }

        public CellEntry atDrive(BlockPos position, ItemStack stack, @Nullable Component reason) {
            return atDrive(position, stack, reason, null);
        }

        public CellEntry atDrive(BlockPos position, ItemStack stack, @Nullable Component reason,
            @Nullable UUID interactionId) {
            return new CellEntry(
                typeId,
                tier,
                kind,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                infinite,
                state,
                online,
                position,
                cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copy(stack),
                reason,
                interactionId,
                panelTierName);
        }

        public CellEntry withPanelTier(StorageTierDescriptor tier) {
            return new CellEntry(
                typeId,
                this.tier,
                kind,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                infinite,
                state,
                online,
                position,
                stack,
                extractionBlockReason,
                interactionId,
                tier.getPanelTierName());
        }

        public CellEntry(int typeId, int tier, int kind, long usedTypes, long totalTypes, long usedBytes,
            long totalBytes, boolean infinite) {
            this(
                typeId,
                tier,
                kind,
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                infinite,
                usedBytes == 0 ? CellState.EMPTY
                    : totalBytes > 0 && usedBytes >= totalBytes ? CellState.FULL
                        : totalTypes > 0 && usedTypes >= totalTypes ? CellState.TYPES_FULL : CellState.NOT_EMPTY,
                true);
        }

        public boolean installed() {
            return tier > 0 || !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack);
        }
        public static final int KIND_EMPTY = 0;
        public static final int KIND_ITEM = 1;
        public static final int KIND_FLUID = 2;
        public static final int KIND_OTHER = 3;
        /** @deprecated The fallback includes all non-item/non-fluid key types. */
        @Deprecated
        public static final int KIND_GAS = KIND_OTHER;
    }

    static List<CellEntry> safeEntries(Supplier<List<CellEntry>> supplier) {
        List<CellEntry> entries = supplier.get();
        return entries == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf() : entries;
    }

    private StorageHostUI() {}
}
