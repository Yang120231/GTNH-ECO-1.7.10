package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;

/**
 * Coordinates infinite-storage mode transitions and the mounted domain lifetime.
 */
final class ECOInfiniteStorageModeController {

    private static final int MINIMUM_MIGRATION_SOURCES = 12;
    private static final String MIGRATION_DOMAIN_STAGE = "migration domain";

    private final ECOStorageSystemBlockEntity host;
    private final ECOInfiniteStorageTransfer transfer = new ECOInfiniteStorageTransfer();
    private final Set<UUID> preparedSourceSeals = new HashSet<>();

    private boolean targetInfiniteMode;
    private boolean updating;
    private long modeCheckTick = Long.MIN_VALUE;
    private long backendGeneration;
    private boolean hostAccess;
    private int migrationProgressHighWater;
    @Nullable
    private String migrationBlockedReason;
    private final java.util.function.LongSupplier migrationClock = System::nanoTime;

    @Nullable
    private MinecraftServer mountedServer;
    @Nullable
    private UUID mountedDomainId;
    @Nullable
    private ECOInfiniteStorageEngine mountedEngine;

    ECOInfiniteStorageModeController(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void update() {
        if (host.getLevel() == null || host.getLevel().isClientSide
            || host.isStorageServerStopping()
            || updating
            || modeCheckTick == host.getLevel()
                .getGameTime()) {
            return;
        }
        modeCheckTick = host.getLevel()
            .getGameTime();
        updating = true;
        ECOStorageHostMode previous = host.storageHostMode();
        try {
            if (host.consumeInfiniteComponentsDirty()) {
                targetInfiniteMode = host.hasRequiredInfiniteComponents();
                // Keep a completed exit latched until the components have actually been removed.
                if (!targetInfiniteMode && !host.storageHostMode()
                    .isInfiniteState()) {
                    host.clearInfiniteExitRequest();
                }
            }
            processMode();
        } finally {
            updating = false;
            syncModeChanges(previous);
        }
    }

    private void processMode() {
        host.rememberInfiniteMembers();
        if (!host.isFormed() || host.getCluster() == null) {
            if (!host.storageHostMode()
                .isInfiniteState()) {
                host.setStorageHostMode(ECOStorageHostMode.UNFORMED);
            }
            return;
        }
        if (host.storageHostMode() == ECOStorageHostMode.UNFORMED) {
            host.setStorageHostMode(ECOStorageHostMode.FORMED_NORMAL);
        }
        if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            // Finish the sealed migration before considering an exit, including after reload.
            runMigrationStep();
            return;
        }
        ECOInfiniteStorageEngine restoringEngine = getEngine();
        if (host.storageHostMode() == ECOStorageHostMode.RESTORING_TO_NORMAL || host.infiniteRestore()
            .isRestoring()
            || restoringEngine != null && restoringEngine.hasPendingRestore()
            || host.isInfiniteExitRequested() && host.storageHostMode()
                .isInfiniteState()) {
            host.setStorageHostMode(ECOStorageHostMode.RESTORING_TO_NORMAL);
            host.infiniteRestore()
                .restoreInfiniteDomainToNormalStorageIfPossible();
            return;
        }
        if (host.storageHostMode()
            .isInfiniteState() && !targetInfiniteMode) {
            host.setStorageHostMode(ECOStorageHostMode.RESTORING_TO_NORMAL);
            host.infiniteRestore()
                .restoreInfiniteDomainToNormalStorageIfPossible();
            return;
        }
        if (host.storageHostMode() == ECOStorageHostMode.FORMED_NORMAL && canStartMigration()) {
            ensureDomainId();
            // Opening the destination must succeed before changing ownership of any source.
            ECOInfiniteStorageEngine engine = getEngine();
            if (engine == null || !engine.isHealthy()) {
                return;
            }
            host.setStorageHostMode(ECOStorageHostMode.MIGRATING_TO_INFINITE);
        }
        if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            runMigrationStep();
        }
    }

    private void syncModeChanges(ECOStorageHostMode previous) {
        if (previous != host.storageHostMode()) {
            host.storageInterfaceTransfer()
                .invalidateInfiniteStorageView();
            host.infiniteRestore()
                .invalidateExtractionCheck();
            host.invalidateStorageStatistics();
            host.refreshDriveStorageProviders();
            host.setChanged();
            host.markForUpdate();
        }
    }

    private boolean canStartMigration() {
        return !host.isInfiniteExitRequested() && host.getTier()
            .supportsFeature(ECOFeature.INFINITE_STORAGE_HOST)
            && host.isFormed()
            && host.getCluster() != null
            && !host.isStorageInterfaceTransferMode()
            && targetInfiniteMode
            && countMigrationSources() >= MINIMUM_MIGRATION_SOURCES
            && !hasForeignMembers()
            && !hasDuplicateCandidateIdentities();
    }

    private boolean hasDuplicateCandidateIdentities() {
        if (host.getCluster() == null) return false;
        Set<UUID> identities = new HashSet<>();
        for (ECODriveBlockEntity drive : host.getCluster()
            .getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)
                || (!ECOInfiniteStorageMember.isMember(stack) && !ECOInfiniteStorageTransfer.isEligible(cell))) {
                continue;
            }
            var identity = ECOInfiniteStorageMember.getIdentity(stack);
            if (identity.isPresent() && !identities.add(identity.get())) return true;
        }
        return false;
    }

    /**
     * Completed drives plus the handled fraction of the drives still migrating. Never decreases during one migration
     * (a cursor rebuilt after a reload restarts its fraction) and reaches 100 only in {@code FORMED_INFINITE}.
     */
    int migrationProgressPercent() {
        if (host.storageHostMode() == ECOStorageHostMode.FORMED_INFINITE) {
            return 100;
        }
        if (host.storageHostMode() != ECOStorageHostMode.MIGRATING_TO_INFINITE || host.getCluster() == null) {
            return 0;
        }
        int migrated = countInfiniteMembers();
        int pending = 0;
        double partial = 0.0D;
        for (ECODriveBlockEntity drive : host.getCluster()
            .getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)
                || ECOInfiniteStorageMember.isMember(stack)
                || !ECOInfiniteStorageTransfer.isEligible(drive.getCellInventory())) {
                continue;
            }
            pending++;
            partial += transfer.progress(ECOInfiniteStorageMember.getMigrationId(stack));
        }
        int total = migrated + pending;
        int percent = total == 0 ? 99 : (int) Math.floor((migrated + partial) * 100.0D / total);
        percent = Math.max(0, Math.min(99, percent));
        migrationProgressHighWater = Math.max(migrationProgressHighWater, percent);
        return migrationProgressHighWater;
    }

    /** Translation key of the current migration blocker, or null while migration advances normally. */
    @Nullable
    String migrationBlockedReason() {
        return host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE ? migrationBlockedReason : null;
    }

    private int countMigrationSources() {
        return countInfiniteMembers() + countPendingMigrationTargets();
    }

    private boolean hasForeignMembers() {
        if (host.getCluster() == null) {
            return false;
        }
        for (ECODriveBlockEntity drive : host.getCluster()
            .getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (ECOInfiniteStorageMember.isMember(stack) && (host.infiniteDomainId() == null
                || !ECOInfiniteStorageMember.isMemberOf(stack, host.infiniteDomainId()))) {
                return true;
            }
        }
        return false;
    }

    private int countPendingMigrationTargets() {
        if (host.getCluster() == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : host.getCluster()
            .getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack != null && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)
                && ECOInfiniteStorageTransfer.isEligible(cell)
                && !ECOInfiniteStorageMember.isMember(stack)) {
                count++;
            }
        }
        return count;
    }

    private int countInfiniteMembers() {
        if (host.getCluster() == null || host.infiniteDomainId() == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : host.getCluster()
            .getDrives()) {
            if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), host.infiniteDomainId())) {
                count++;
            }
        }
        return count;
    }

    private void runMigrationStep() {
        if (!(host.getLevel() instanceof ServerWorldView serverLevel) || host.getCluster() == null) {
            return;
        }
        UUID domainId = ensureDomainId();
        ECOInfiniteStorageEngine engine = getEngine();
        long now = host.getLevel()
            .getGameTime();
        if (engine == null || !engine.isHealthy()) {
            // Reported, not retried later: the engine answers again next tick and the fault clears on recovery.
            String reason = engine == null ? null : engine.migrationBlockedReason(null);
            migrationBlockedReason = reason == null ? ECOInfiniteStorageTransfer.BLOCKED_DOMAIN_UNAVAILABLE : reason;
            host.storageFaults()
                .report(MIGRATION_DOMAIN_STAGE, migrationBlockedReason, now);
            return;
        }
        host.storageFaults()
            .recovered(MIGRATION_DOMAIN_STAGE);
        sealTransferSources(serverLevel, domainId);
        boolean hasPending = false;
        String blocked = null;
        // Drives migrate in order, sharing one budget: a finished drive hands the rest of the tick to the next one.
        ECOInfiniteStorageTransfer.Budget budget = ECOInfiniteStorageTransfer.Budget.perTick(
            migrationClock,
            cn.dancingsnow.neoecoae.platform.forge.PlanningSettings.Storage.infiniteMigrationMicrosPerTick,
            cn.dancingsnow.neoecoae.platform.forge.PlanningSettings.Storage.infiniteMigrationKeysPerTick);
        List<ECODriveBlockEntity> drives = new ArrayList<>(
            host.getCluster()
                .getDrives());
        for (ECODriveBlockEntity drive : drives) {
            String stage = "migration drive " + drive.getBlockPos();
            long tick = now;
            if (tick < host.storageStageRetryTicks()
                .getOrDefault(stage, Long.MIN_VALUE)) {
                hasPending = true;
                continue;
            }
            try {
                ItemStack stack = drive.getCellStack();
                if (ECOInfiniteStorageMember.isMember(stack)) {
                    if (ECOInfiniteStorageMember.isMemberOf(stack, domainId)) {
                        continue;
                    }
                    hasPending = true;
                    host.storageFaults()
                        .report(stage, "Foreign infinite storage member", tick);
                    continue;
                }
                IECOStorageCell cell = drive.getCellInventory();
                if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)
                    || !ECOInfiniteStorageTransfer.isEligible(cell)) {
                    continue;
                }
                if (!preparedSourceSeals.contains(ECOInfiniteStorageMember.getMigrationId(stack))
                    || budget.exhausted()) {
                    hasPending = true;
                    continue;
                }
                ECOInfiniteStorageTransfer.Result result = migrateDrive(drive, cell, engine, domainId, budget);
                if (result.isBlocked()) {
                    // Blocked is a state, not a failure: no exception and no cooldown, re-checked every tick.
                    if (blocked == null) blocked = result.reason();
                    host.storageFaults()
                        .report(stage, result.describe(), tick);
                } else {
                    host.storageFaults()
                        .recovered(stage);
                }
                if (!result.isComplete()) hasPending = true;
            } catch (RuntimeException exception) {
                hasPending = true;
                host.storageStageRetryTicks()
                    .put(stage, tick + 200L);
                host.storageFaults()
                    .report(stage, exception.toString(), tick, exception);
            }
        }
        migrationBlockedReason = blocked;
        if (!hasPending && host.infiniteMigrationSourceIds()
            .isEmpty()) {
            host.setStorageHostMode(ECOStorageHostMode.FORMED_INFINITE);
        }
    }

    private ECOInfiniteStorageTransfer.Result migrateDrive(ECODriveBlockEntity drive, IECOStorageCell cell,
        ECOInfiniteStorageEngine engine, UUID domainId, ECOInfiniteStorageTransfer.Budget budget) {
        if (!(cell instanceof IECOStorageMigrationCell migrationCell)) {
            throw new IllegalStateException("Cell handler does not support resumable migration");
        }
        ItemStack source = drive.getCellStack();
        UUID migration = ECOInfiniteStorageMember.beginMigration(source, domainId);
        long packedPos = drive.getBlockPos()
            .asLong();
        ECOInfiniteStorageTransfer.Result result = transfer.step(
            source,
            migrationCell,
            domainId,
            engine,
            host.getLevel()
                .registryAccess(),
            () -> {
                if (!preparedSourceSeals.contains(migration)) {
                    throw new IllegalStateException("Source seal has not been prepared");
                }
            },
            () -> drive.convertCellToInfiniteMember(domainId),
            (key, keyTag, amount) -> ECOInfiniteStorageTransfer
                .slotReceipt(domainId, "to-domain", packedPos, keyTag, amount),
            budget);
        if (!result.isComplete()) {
            return result;
        }
        host.rememberInfiniteMembers();
        preparedSourceSeals.remove(migration);
        host.infiniteMigrationSourceIds()
            .remove(migration);
        IStorageProvider.requestUpdate(drive.getMainNode());
        host.invalidateStorageStatistics();
        drive.setChanged();
        host.setChanged();
        host.markForUpdate();
        return result;
    }

    private void sealTransferSources(ServerWorldView serverLevel, UUID domainId) {
        try (var ignored = cn.dancingsnow.neoecoae.platform.ae2.grid.NativeCellArrayUpdates.coalesce()) {
            sealTransferSourcesCoalesced(serverLevel, domainId);
        }
    }

    private void sealTransferSourcesCoalesced(ServerWorldView serverLevel, UUID domainId) {
        Set<UUID> prepared = new HashSet<>();
        for (ECODriveBlockEntity drive : host.getCluster()
            .getDrives()) {
            if (prepared.size() >= ECOInfiniteStorageTransfer.SEAL_SOURCES_PER_TICK) break;
            String stage = "migration drive " + drive.getBlockPos();
            if (host.getLevel()
                .getGameTime()
                < host.storageStageRetryTicks()
                    .getOrDefault(stage, Long.MIN_VALUE)) {
                continue;
            }
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)
                || ECOInfiniteStorageMember.isMember(stack)
                || !ECOInfiniteStorageTransfer.isEligible(cell)) {
                continue;
            }
            try {
                UUID migration = ECOInfiniteStorageMember.beginMigration(stack, domainId);
                if (host.infiniteMigrationSourceIds()
                    .add(migration)) {
                    host.setChanged();
                }
                if (preparedSourceSeals.contains(migration)) {
                    continue;
                }
                ((IECOStorageMigrationCell) cell).persistMigrationContents(serverLevel.nativeWorld());
                drive.setChanged();
                IStorageProvider.requestUpdate(drive.getMainNode());
                prepared.add(migration);
            } catch (RuntimeException exception) {
                host.storageStageRetryTicks()
                    .put(
                        stage,
                        host.getLevel()
                            .getGameTime() + 200L);
                host.storageFaults()
                    .report(
                        stage,
                        exception.toString(),
                        host.getLevel()
                            .getGameTime(),
                        exception);
            }
        }
        if (!prepared.isEmpty()) {
            // Chunks and the domain are saved together by the next vanilla world save; receipts make a replay safe.
            host.setChanged();
            preparedSourceSeals.addAll(prepared);
        }
    }

    UUID migrationTransactionId(UUID domainId, ECODriveBlockEntity drive, AEKey key, long amount, String direction) {
        ECOInfiniteStorageEngine engine = getEngine();
        UUID restore = engine == null ? null : engine.restoreTransaction(key);
        if ("from-domain".equals(direction) && restore != null) {
            UUID identity = ECOInfiniteStorageMember.identity(drive.getCellStack());
            drive.setChanged();
            return UUID.nameUUIDFromBytes((restore + ":" + identity).getBytes(StandardCharsets.UTF_8));
        }
        String value = domainId + ":"
            + direction
            + ":"
            + drive.getBlockPos()
                .asLong()
            + ":"
            + key.toTagGeneric(
                host.getLevel()
                    .registryAccess())
            + ":"
            + amount;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    void exitIfSafe() {
        if (host.getMissingInfiniteMembers() > 0) {
            return;
        }
        ECOInfiniteStorageEngine engine = getEngine();
        if (engine == null || !engine.canExitOrRestore()
            || !engine.isEmpty()
            || !engine.commit()
                .successful()) {
            return;
        }
        UUID domainId = host.infiniteDomainId();
        if (host.getCluster() != null && domainId != null) {
            try (var ignored = cn.dancingsnow.neoecoae.platform.ae2.grid.NativeCellArrayUpdates.coalesce()) {
                for (ECODriveBlockEntity drive : host.getCluster()
                    .getDrives()) {
                    if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), domainId)) {
                        drive.convertInfiniteMemberToNormalStorage(domainId);
                        IStorageProvider.requestUpdate(drive.getMainNode());
                    }
                }
            }
        }
        host.setStorageHostMode(host.isFormed() ? ECOStorageHostMode.FORMED_NORMAL : ECOStorageHostMode.UNFORMED);
        if (host.getLevel() instanceof ServerWorldView && domainId != null) {
            // Retain transfer receipts: an old source chunk must not import the same inventory again.
            release();
        }
        host.setInfiniteDomainId(null);
        host.infiniteMemberIds()
            .clear();
        host.infiniteMigrationSourceIds()
            .clear();
        host.refreshDriveStorageProviders();
        host.setChanged();
        host.markForUpdate();
    }

    @Nullable
    ECOInfiniteStorageEngine getEngine() {
        if (!(host.getLevel() instanceof ServerWorldView serverLevel) || host.infiniteDomainId() == null) {
            release();
            return null;
        }
        MinecraftServer server = serverLevel.getServer()
            .nativeServer();
        if (mountedEngine == null || mountedServer != server
            || !host.infiniteDomainId()
                .equals(mountedDomainId)) {
            release();
            mountedServer = server;
            mountedDomainId = host.infiniteDomainId();
            mountedEngine = ECOInfiniteStorageDomains.acquire(serverLevel.nativeWorld(), host.infiniteDomainId());
            refreshHostAccess();
        }
        return mountedEngine;
    }

    void release() {
        backendGeneration++;
        hostAccess = false;
        transfer.reset();
        preparedSourceSeals.clear();
        if (mountedServer != null && mountedDomainId != null) {
            ECOInfiniteStorageDomains.release(mountedServer, mountedDomainId);
        }
        mountedServer = null;
        mountedDomainId = null;
        mountedEngine = null;
        host.storageInterfaceTransfer()
            .invalidateInfiniteStorageView();
    }

    UUID ensureDomainId() {
        UUID domainId = host.infiniteDomainId();
        if (domainId == null) {
            domainId = UUID.randomUUID();
            host.setInfiniteDomainId(domainId);
            host.setChanged();
        }
        return domainId;
    }

    MEStorage createStorageView(ECOInfiniteStorageEngine engine) {
        refreshHostAccess();
        long generation = backendGeneration;
        return new ECOInfiniteStorage(
            engine,
            cn.dancingsnow.neoecoae.platform.minecraft.Component.literal(
                host.getBlockState()
                    .getBlock()
                    .getLocalizedName()),
            () -> generation == backendGeneration && engine == mountedEngine
                && hostAccess
                && !host.isStorageServerStopping()
                && engine.canUseMountedStorage());
    }

    void hostModeChanged() {
        backendGeneration++;
        migrationProgressHighWater = 0;
        migrationBlockedReason = null;
        refreshHostAccess();
    }

    void membersChanged() {
        boolean previouslyAccessible = hostAccess;
        refreshHostAccess();
        if (previouslyAccessible != hostAccess && host.getLevel() != null
            && !host.getLevel().isClientSide
            && !host.isStorageServerStopping()) {
            host.refreshDriveStorageProviders();
        }
    }

    /** Called synchronously by host transitions; domain health and restore locks are engine-owned. */
    void refreshHostAccess() {
        hostAccess = host.isFormed() && !host.isRemoved()
            && host.storageHostMode() == ECOStorageHostMode.FORMED_INFINITE
            && host.infiniteDomainId() != null
            && !host.isInfiniteExitRequested()
            && !host.infiniteRestore()
                .isRestoring()
            && mountedEngine != null
            && host.getMissingInfiniteMembers() == 0;
    }

    boolean canInsertIntoDomain() {
        return hostAccess && !host.isStorageServerStopping() && mountedEngine.canUseMountedStorage();
    }
}
