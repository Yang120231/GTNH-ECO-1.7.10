package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import appeng.helpers.IPriorityHost;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEStorageItems;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.computation.ComputationTaskInfo;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipe;
import cn.dancingsnow.neoecoae.crafting.cooling.ECOCoolingRecipes;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingCapacity;
import cn.dancingsnow.neoecoae.energy.ECOEnergyProfile;
import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode;
import cn.dancingsnow.neoecoae.gui.computation.ComputationHostStats;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationResult;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationScanner;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cn.dancingsnow.neoecoae.storage.ae2.ECOStorageDriveProvider;
import cn.dancingsnow.neoecoae.storage.ae2.ECOStorageInterfaceTransfer;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageDomainData;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata;

public class TileECOController extends TileEntity implements IInventory, IPriorityHost, IActionHost {

    private static final String TAG_SUBSYSTEM = "Subsystem";
    private static final String TAG_TIER = "Tier";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_MIRRORED = "Mirrored";
    private static final String TAG_FACING_META = "FacingMeta";
    private static final String TAG_HIDDEN_BLOCKS = "HiddenBlocks";
    private static final String TAG_FORMED_MEMBER_BLOCKS = "FormedMemberBlocks";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_MEMBER_TIER = "MemberTier";
    private static final String TAG_INFINITE_COMPONENT = "InfiniteStorageComponent";
    private static final String TAG_HOST_MODE = "HostMode";
    private static final String TAG_HOST_DOMAIN_ID = "HostDomainId";
    private static final String TAG_MEMBER_DISKS = "MemberDiskIds";
    private static final String TAG_MIGRATION_STEPS = "MigrationSteps";
    private static final String TAG_PRIORITY = "Priority";
    private static final String TAG_COMPUTATION_CPU_MODE = "ComputationCpuMode";
    private static final String TAG_CRAFTING_PATTERN_COUNT = "CraftingPatternCount";
    private static final String TAG_CRAFTING_PATTERN_BUS_COUNT = "CraftingPatternBusCount";
    private static final String TAG_CRAFTING_WORKER_COUNT = "CraftingWorkerCount";
    private static final String TAG_CRAFTING_RUNNING_WORKER_COUNT = "CraftingRunningWorkerCount";
    private static final String TAG_CRAFTING_PARALLEL_COUNT = "CraftingParallelCount";
    private static final String TAG_CRAFTING_PARALLEL_CORE_COUNT = "CraftingParallelCoreCount";
    private static final String TAG_CRAFTING_INPUT_CACHED_ITEMS = "CraftingInputCachedItems";
    private static final String TAG_CRAFTING_OUTPUT_CACHED_ITEMS = "CraftingOutputCachedItems";
    private static final String TAG_CRAFTING_OCCUPIED_CACHE_SLOTS = "CraftingOccupiedCacheSlots";
    private static final String TAG_CRAFTING_VIRTUAL_POOL = "CraftingVirtualPool";
    private static final String TAG_CRAFTING_OVERCLOCKED = "CraftingOverclocked";
    private static final String TAG_CRAFTING_ACTIVE_COOLING = "CraftingActiveCooling";
    private static final String TAG_CRAFTING_COOLANT = "CraftingCoolant";
    private static final String TAG_CRAFTING_COOLANT_MAX_OVERCLOCK = "CraftingCoolantMaxOverclock";
    private static final String TAG_CRAFTING_PLANNER_ACCEPTED = "CraftingFastPathHits";
    private static final String TAG_CRAFTING_PLANNER_REJECTED = "CraftingFastPathFallbacks";
    private static final String TAG_DISK_ID = "DiskId";
    private static final String TAG_STEP = "Step";
    private static final int REQUIRED_INFINITE_COMPONENTS = 64;
    private static final int REQUIRED_INFINITE_DRIVES = 16;
    public static final int MAX_CRAFTING_COOLANT = 1000000;
    public static final int CRAFTING_ENERGY_GAUGE_REFERENCE = ECOEnergyProfile.CRAFTING_ENERGY_GAUGE_REFERENCE;
    private static final int MIGRATION_NOT_STARTED = 0;
    private static final int MIGRATION_COPYING = 1;
    private static final int MIGRATION_SOURCE_CLEARED = 2;
    private static final int MIGRATION_BOUND_AS_MEMBER = 3;
    private static final int CRAFTING_OUTPUT_DRAIN_INTERVAL = 5;
    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L;

    private ECOControllerSubsystem subsystem = ECOControllerSubsystem.STORAGE;
    private ECOControllerTier tier = ECOControllerTier.L4;
    private boolean formed;
    private boolean mirrored;
    private int facingMeta;
    private String lastFormationMessage = "not scanned";
    private final List<ECOFormationBlockPos> hiddenBlocks = new ArrayList<>();
    private final List<ECOFormationBlockPos> formedMemberBlocks = new ArrayList<>();
    private ItemStack infiniteStorageComponent;
    private ECOStorageHostMode hostMode = ECOStorageHostMode.UNFORMED;
    private UUID hostDomainId;
    private int storageBackendRevision;
    private int priority;
    private ComputationCpuSelectionMode computationCpuSelectionMode = ComputationCpuSelectionMode.ANY;
    private ComputationHostStats computationHostStats = ComputationHostStats.EMPTY;
    private boolean computationHostStatsDirty = true;
    private CraftingHostStats craftingHostStats = CraftingHostStats.EMPTY;
    private boolean craftingHostStatsDirty = true;
    private boolean craftingOverclocked;
    private boolean craftingActiveCooling;
    private int craftingCoolant;
    private int craftingCoolantMaxOverclock;
    private int craftingPlannerAccepted;
    private int craftingPlannerRejected;
    private long craftingPerformanceWindowStartTick = Long.MIN_VALUE;
    private long craftingPerformanceWindowNanos;
    private long craftingPerformanceAverageNanos;
    private boolean computationInterfaceOnline;
    private final List<UUID> memberDiskIds = new ArrayList<>();
    private final Map<UUID, Integer> migrationSteps = new LinkedHashMap<>();
    private CraftingMemberCache craftingMemberCache = CraftingMemberCache.EMPTY;
    private final ECOCraftingVirtualPool craftingVirtualPool = new ECOCraftingVirtualPool();
    private boolean craftingMemberCacheDirty = true;
    private int craftingMemberCacheRevision = 0;
    private boolean hostDomainClientUpdatePending;

    public TileECOController() {}

    public TileECOController(ECOControllerSubsystem subsystem, ECOControllerTier tier) {
        this.subsystem = subsystem;
        this.tier = tier;
    }

    public ECOControllerSubsystem getSubsystem() {
        return this.subsystem;
    }

    public ECOControllerTier getTier() {
        return this.tier;
    }

    public boolean isFormed() {
        return this.formed;
    }

    public void setFormed(boolean formed) {
        if (this.formed == formed) {
            return;
        }

        this.formed = formed;
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
            this.worldObj.markBlockRangeForRenderUpdate(
                this.xCoord,
                this.yCoord,
                this.zCoord,
                this.xCoord,
                this.yCoord,
                this.zCoord);
        }
    }

    public boolean isMirrored() {
        return this.mirrored;
    }

    public ECOStorageHostMode getHostMode() {
        return this.hostMode;
    }

    public List<ECOFormationBlockPos> getFormedMemberBlocks() {
        return new ArrayList<>(this.formedMemberBlocks);
    }

    public List<ECOFormationBlockPos> getHiddenBlocks() {
        return new ArrayList<>(this.hiddenBlocks);
    }

    boolean hasFormedMemberBlock(int x, int y, int z) {
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    boolean hasHiddenMemberBlock(int x, int y, int z) {
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    boolean hasCraftingMemberBlock(int x, int y, int z) {
        return this.hasFormedMemberBlock(x, y, z) || this.hasHiddenMemberBlock(x, y, z);
    }

    boolean hasOnlineStorageInterface() {
        return this.hasOnlineInterface(ECOControllerSubsystem.STORAGE);
    }

    public TileECOInterface getStorageInterfaceForTransfer() {
        if (this.worldObj == null || this.subsystem != ECOControllerSubsystem.STORAGE || !this.formed) {
            return null;
        }
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileECOInterface ecoInterface
                && ecoInterface.getSubsystem() == ECOControllerSubsystem.STORAGE) {
                return ecoInterface;
            }
        }
        return null;
    }

    private boolean hasOnlineInterface(ECOControllerSubsystem targetSubsystem) {
        return this.findOnlineInterfaceNode(targetSubsystem) != null;
    }

    private IGridNode findOnlineInterfaceNode(ECOControllerSubsystem targetSubsystem) {
        if (this.worldObj == null) {
            return null;
        }
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileECOInterface ecoInterface) {
                if (ecoInterface.getSubsystem() == targetSubsystem && ecoInterface.isNetworkOnline()) {
                    return ecoInterface.getActionableNode();
                }
            }
        }
        return null;
    }

    @Override
    public IGridNode getActionableNode() {
        return this.findOnlineInterfaceNode(this.subsystem);
    }

    @Override
    public IGridNode getGridNode(ForgeDirection direction) {
        return null;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection direction) {
        return AECableType.NONE;
    }

    @Override
    public void securityBreak() {
        // The controller delegates AE security checks to its formed interface and is not itself a cable endpoint.
    }

    public int getRequiredInfiniteDriveCount() {
        return REQUIRED_INFINITE_DRIVES;
    }

    public boolean areAllFormedDrivesL9MatricesForDisplay() {
        return this.areAllFormedDrivesL9Matrices();
    }

    public boolean isHiddenMember(int x, int y, int z) {
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    public UUID getHostDomainId() {
        return this.hostDomainId;
    }

    public boolean canUseHostDomainStorage() {
        return this.formed && this.hostMode == ECOStorageHostMode.FORMED_INFINITE && this.hostDomainId != null;
    }

    /**
     * Makes a newly formed, empty infinite host point at an orphaned domain.
     *
     * <p>
     * This operation never merges storage. The target domain created by the new host must be
     * empty, and all target matrices must still be empty domain members. That makes the operation
     * restart-safe for the intended "new host takes over old domain" workflow and prevents an
     * accidental overwrite of real data.
     * </p>
     *
     * @return a stable result id for the recovery terminal's server-side message
     */
    public String adoptRecoveredDomain(UUID sourceDomainId) {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return "invalid_target";
        }
        if (this.subsystem != ECOControllerSubsystem.STORAGE || !this.formed
            || this.tier != ECOControllerTier.L9
            || this.hostMode != ECOStorageHostMode.FORMED_INFINITE
            || this.hostDomainId == null) {
            return "invalid_target";
        }
        if (sourceDomainId == null) {
            return "source_missing";
        }
        if (sourceDomainId.equals(this.hostDomainId)) {
            return this.repairRecoveredDomain(sourceDomainId);
        }

        ECOStorageDomainData data = ECOStorageDomainData.get(this.worldObj);
        if (data.getDomain(sourceDomainId) == null) {
            return "source_missing";
        }
        if (ECOControllerRegistry.isDomainBound(this.worldObj, sourceDomainId, this)) {
            return "source_bound";
        }

        UUID oldDomainId = this.hostDomainId;
        if (!data.isDomainEmpty(oldDomainId)) {
            return "target_not_empty";
        }

        List<TileECODrive> drives = new ArrayList<>();
        List<UUID> diskIds = new ArrayList<>();
        Set<UUID> seenDiskIds = new HashSet<>();
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (!(tile instanceof TileECODrive)) {
                return "target_invalid";
            }
            TileECODrive drive = (TileECODrive) tile;
            ItemStack stack = drive.getCellStack();
            if (isNotL9StorageMatrix(stack) || !ECOStorageCellMetadata.hasNonPortableState(stack)
                || !oldDomainId.equals(ECOStorageCellMetadata.getHostDomainId(stack))) {
                return "target_invalid";
            }
            ECOStorageBackend localStorage = ECOStorageCellAccess.load(stack);
            if (!localStorage.isEmpty()) {
                return "target_not_empty";
            }
            UUID diskId = ECOStorageCellMetadata.getDiskId(stack);
            if (diskId == null) {
                diskId = ECOStorageCellMetadata.getOrCreateDiskId(stack);
                drive.markDirty();
            }
            if (!seenDiskIds.add(diskId)) {
                return "target_invalid";
            }
            drives.add(drive);
            diskIds.add(diskId);
        }
        if (drives.size() < REQUIRED_INFINITE_DRIVES) {
            return "target_invalid";
        }

        // Publish the new pointer before changing member metadata. If the server stops between
        // two member writes, selecting this same UUID again can repair the remaining empty members.
        this.hostDomainId = sourceDomainId;
        this.memberDiskIds.clear();
        this.memberDiskIds.addAll(diskIds);
        this.migrationSteps.clear();
        this.hostMode = ECOStorageHostMode.FORMED_INFINITE;
        this.storageBackendRevision++;
        this.markDirty();

        // All validation is complete before any member binding is changed.
        for (int i = 0; i < drives.size(); i++) {
            TileECODrive drive = drives.get(i);
            ItemStack stack = drive.getCellStack();
            UUID diskId = diskIds.get(i);
            data.forgetCommittedSource(oldDomainId, diskId);
            ECOStorageCellMetadata.markDomainMember(stack, sourceDomainId, i);
            ECOStorageCellMetadata.writeSummary(stack, 0L, 0);
            drive.discardCellBackend();
            drive.markDirty();
        }

        data.replaceCommittedSources(sourceDomainId, diskIds);
        data.removeDomain(oldDomainId);
        this.markDirty();
        this.hostDomainClientUpdatePending = true;
        this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        return "recovered";
    }

    private String repairRecoveredDomain(UUID sourceDomainId) {
        if (this.worldObj == null || this.hostMode != ECOStorageHostMode.FORMED_INFINITE
            || this.subsystem != ECOControllerSubsystem.STORAGE) {
            return "invalid_target";
        }
        if (ECOControllerRegistry.isDomainBound(this.worldObj, sourceDomainId, this)) {
            return "source_bound";
        }
        ECOStorageDomainData data = ECOStorageDomainData.get(this.worldObj);
        if (data.getDomain(sourceDomainId) == null) {
            return "source_missing";
        }
        List<UUID> diskIds = new ArrayList<>();
        Set<UUID> seenDiskIds = new HashSet<>();
        for (int i = 0; i < this.formedMemberBlocks.size(); i++) {
            ECOFormationBlockPos pos = this.formedMemberBlocks.get(i);
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (!(tile instanceof TileECODrive)) {
                return "target_invalid";
            }
            TileECODrive drive = (TileECODrive) tile;
            ItemStack stack = drive.getCellStack();
            if (isNotL9StorageMatrix(stack) || !ECOStorageCellMetadata.hasNonPortableState(stack)
                || !ECOStorageCellAccess.load(stack)
                    .isEmpty()) {
                return "target_not_empty";
            }
            UUID diskId = ECOStorageCellMetadata.getDiskId(stack);
            if (diskId == null || !seenDiskIds.add(diskId)) {
                return "target_invalid";
            }
            if (!sourceDomainId.equals(ECOStorageCellMetadata.getHostDomainId(stack))) {
                ECOStorageCellMetadata.markDomainMember(stack, sourceDomainId, i);
                ECOStorageCellMetadata.writeSummary(stack, 0L, 0);
                drive.discardCellBackend();
                drive.markDirty();
            }
            diskIds.add(diskId);
        }
        if (diskIds.size() < REQUIRED_INFINITE_DRIVES) {
            return "target_invalid";
        }
        this.memberDiskIds.clear();
        this.memberDiskIds.addAll(diskIds);
        this.migrationSteps.clear();
        data.replaceCommittedSources(sourceDomainId, diskIds);
        this.storageBackendRevision++;
        this.markDirty();
        this.hostDomainClientUpdatePending = true;
        this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        return "recovered";
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int priority) {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return;
        }
        if (this.priority == priority) {
            return;
        }
        this.priority = priority;
        this.onStorageBackendChanged();
    }

    public ComputationCpuSelectionMode getComputationCpuSelectionMode() {
        return this.computationCpuSelectionMode;
    }

    public ComputationHostStats getComputationHostStats() {
        if (this.subsystem != ECOControllerSubsystem.COMPUTATION) {
            return ComputationHostStats.EMPTY;
        }
        if (this.computationHostStatsDirty) {
            this.refreshComputationHostStats();
        }
        return this.computationHostStats;
    }

    public CraftingHostStats getCraftingHostStats() {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return CraftingHostStats.EMPTY;
        }
        if (this.craftingHostStatsDirty) {
            this.refreshCraftingHostStats();
        }
        return this.craftingHostStats;
    }

    public int getCraftingWorkerCount() {
        return this.getCraftingHostStats().workerCount;
    }

    public int getCraftingParallelCount() {
        return this.getCraftingHostStats().parallelCount;
    }

    public List<TileCraftingPatternBus> getCraftingPatternBuses() {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING || this.worldObj == null || !this.formed) {
            return Collections.emptyList();
        }
        CraftingMemberCache cache = this.getCraftingMemberCache();
        return new ArrayList<>(cache.patternBuses());
    }

    public boolean lacksVirtualCraftingCapacity() {
        return this.subsystem != ECOControllerSubsystem.CRAFTING || this.worldObj == null
            || !this.formed
            || this.getCraftingMemberCache()
                .workers()
                .isEmpty()
            || this.getCraftingCurrentBatchSlots() <= 0;
    }

    public boolean acceptVirtualCraftingBatch(appeng.api.networking.crafting.ICraftingPatternDetails details,
        net.minecraft.inventory.InventoryCrafting table, int craftCount, String jobId) {
        return this.craftingVirtualPool.accept(this, details, table, craftCount, jobId);
    }

    public void recoverVirtualCraftingJob(String jobId) {
        this.craftingVirtualPool.recoverJob(jobId);
        this.onCraftingVirtualPoolStateChanged();
    }

    public void recoverVirtualCraftingUnfinishedInputs(String jobId) {
        this.craftingVirtualPool.recoverUnfinishedInputs(jobId);
        this.onCraftingVirtualPoolStateChanged();
    }

    int getVirtualCraftingOccupiedSlots() {
        return this.craftingVirtualPool.occupiedSlots();
    }

    boolean isVirtualCraftingRunning() {
        return this.craftingVirtualPool.isRunning();
    }

    public List<TileCraftingWorker.WorkSnapshot> getVirtualCraftingWorkSnapshots(int limit) {
        return this.craftingVirtualPool.snapshots(this.getCraftingMaxInFlightCrafts(), limit);
    }

    public long acceptCraftingOutputAmount(ItemStack prototype, long amount) {
        if (prototype == null || amount <= 0L) {
            return 0L;
        }
        if (this.subsystem != ECOControllerSubsystem.CRAFTING || this.worldObj == null || !this.formed) {
            return 0L;
        }
        long remaining = this.injectCraftingOutputAmountToNetwork(prototype, amount, true);
        remaining = this.insertCraftingOutputAmountToHatches(prototype, remaining);
        return amount - remaining;
    }

    public long acceptCraftingRecoveryAmount(ItemStack prototype, long amount) {
        if (prototype == null || amount <= 0L) {
            return 0L;
        }
        if (this.subsystem != ECOControllerSubsystem.CRAFTING || this.worldObj == null || !this.formed) {
            return 0L;
        }
        return amount - this.injectCraftingOutputAmountToNetwork(prototype, amount, false);
    }

    private long injectCraftingOutputAmountToNetwork(ItemStack prototype, long amount, boolean offerToCraftingCpus) {
        long remaining = amount;
        CraftingMemberCache cache = this.getCraftingMemberCache();
        for (TileECOInterface ecoInterface : cache.craftingInterfaces()) {
            if (!ecoInterface.isNetworkOnline()) {
                continue;
            }
            remaining = offerToCraftingCpus ? ecoInterface.injectCraftingOutput(prototype, remaining)
                : ecoInterface.injectCraftingRecovery(prototype, remaining);
            if (remaining <= 0L) {
                return 0L;
            }
        }
        return remaining;
    }

    private long insertCraftingOutputAmountToHatches(ItemStack prototype, long amount) {
        long remaining = amount;
        int maxChunk = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0L) {
            ItemStack chunk = prototype.copy();
            chunk.stackSize = (int) Math.min(remaining, maxChunk);
            ItemStack leftover = this.insertCraftingOutputToHatch(chunk);
            int left = leftover == null ? 0 : Math.max(0, Math.min(chunk.stackSize, leftover.stackSize));
            int accepted = chunk.stackSize - left;
            remaining -= accepted;
            if (accepted <= 0 || left > 0) {
                break;
            }
        }
        return remaining;
    }

    private ItemStack injectCraftingOutputToNetwork(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        ItemStack remaining = stack.copy();

        CraftingMemberCache cache = this.getCraftingMemberCache();
        for (TileECOInterface ecoInterface : cache.craftingInterfaces()) {
            if (!ecoInterface.isNetworkOnline()) {
                continue;
            }
            remaining = ecoInterface.injectCraftingOutput(remaining);
            if (remaining == null) {
                return null;
            }
            if (remaining.stackSize <= 0) {
                return null;
            }
        }
        return remaining;
    }

    private ItemStack insertCraftingOutputToHatch(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        ItemStack remaining = stack.copy();

        CraftingMemberCache cache = this.getCraftingMemberCache();
        for (TileCraftingHatch hatch : cache.outputHatches()) {
            remaining = hatch.insertOutputRemainder(remaining);
            if (remaining == null) {
                return null;
            }
        }
        return remaining;
    }

    private void drainCraftingOutputHatchesToNetwork() {
        if (this.worldObj == null || this.worldObj.isRemote
            || this.subsystem != ECOControllerSubsystem.CRAFTING
            || !this.formed) {
            return;
        }
        CraftingMemberCache cache = this.getCraftingMemberCache();
        for (TileCraftingHatch hatch : cache.outputHatches()) {
            for (int slot = 0; slot < hatch.getSizeInventory(); slot++) {
                ItemStack stored = hatch.getStackInSlot(slot);
                if (stored == null) {
                    continue;
                }
                ItemStack remaining = this.injectCraftingOutputToNetwork(stored);
                if (remaining == null) {
                    hatch.setInventorySlotContents(slot, null);
                } else if (remaining.stackSize < stored.stackSize) {
                    hatch.setInventorySlotContents(slot, remaining);
                    return;
                }
            }
        }
    }

    public int getCraftingWorkQueueDepth() {
        return this.getCraftingHostStats().queuedWorkCount;
    }

    public int getCraftingWorkQueueCapacity() {
        return this.getCraftingMaxInFlightCrafts();
    }

    public int getCraftingPlannerAcceptedCount() {
        return this.craftingPlannerAccepted;
    }

    public int getCraftingPlannerRejectedCount() {
        return this.craftingPlannerRejected;
    }

    public long getCraftingPerformanceAverageNanos() {
        return Math.max(0L, this.craftingPerformanceAverageNanos);
    }

    /** Accumulates real worker execution time and publishes a per-tick 20 tick moving window. */
    public void recordCraftingPerformanceSample(long elapsedNanos) {
        if (elapsedNanos < 0L || this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        long currentTick = this.worldObj.getTotalWorldTime();
        if (this.craftingPerformanceWindowStartTick == Long.MIN_VALUE
            || currentTick < this.craftingPerformanceWindowStartTick) {
            this.craftingPerformanceWindowStartTick = currentTick;
            this.craftingPerformanceWindowNanos = 0L;
        }
        long remaining = Long.MAX_VALUE - this.craftingPerformanceWindowNanos;
        this.craftingPerformanceWindowNanos += Math.min(remaining, elapsedNanos);
        long elapsedTicks = currentTick - this.craftingPerformanceWindowStartTick;
        if (elapsedTicks >= PERFORMANCE_SAMPLE_WINDOW_TICKS) {
            this.craftingPerformanceAverageNanos = this.craftingPerformanceWindowNanos / elapsedTicks;
            this.craftingPerformanceWindowStartTick = currentTick;
            this.craftingPerformanceWindowNanos = 0L;
        }
    }

    public void recordCraftingPlannerDecision(boolean accepted) {
        if (this.worldObj != null && this.worldObj.isRemote || this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return;
        }
        if (accepted) {
            this.craftingPlannerAccepted = saturatedIncrement(this.craftingPlannerAccepted);
        } else {
            this.craftingPlannerRejected = saturatedIncrement(this.craftingPlannerRejected);
        }
        this.markDirty();
    }

    public boolean isCraftingOverclocked() {
        return this.craftingOverclocked;
    }

    public void setCraftingOverclocked(boolean overclocked) {
        if (this.worldObj != null && this.worldObj.isRemote || this.subsystem != ECOControllerSubsystem.CRAFTING
            || this.craftingOverclocked == overclocked) {
            return;
        }
        this.craftingOverclocked = overclocked;
        this.onCraftingHostStateChanged();
    }

    public void toggleCraftingOverclocked() {
        this.setCraftingOverclocked(!this.craftingOverclocked);
    }

    public boolean isCraftingActiveCooling() {
        return this.craftingActiveCooling;
    }

    public void setCraftingActiveCooling(boolean activeCooling) {
        if (this.worldObj != null && this.worldObj.isRemote || this.subsystem != ECOControllerSubsystem.CRAFTING
            || this.craftingActiveCooling == activeCooling) {
            return;
        }
        this.craftingActiveCooling = activeCooling;
        this.onCraftingHostStateChanged();
    }

    public void toggleCraftingActiveCooling() {
        this.setCraftingActiveCooling(!this.craftingActiveCooling);
    }

    public int getCraftingCoolant() {
        return Math.max(0, Math.min(MAX_CRAFTING_COOLANT, this.craftingCoolant));
    }

    public int getCraftingMaxCoolant() {
        return MAX_CRAFTING_COOLANT;
    }

    public void clearCraftingCoolant() {
        if (this.worldObj != null && this.worldObj.isRemote || this.subsystem != ECOControllerSubsystem.CRAFTING
            || this.craftingCoolant == 0) {
            return;
        }
        this.craftingCoolant = 0;
        this.craftingCoolantMaxOverclock = 0;
        this.craftingHostStatsDirty = true;
        this.refreshCraftingHostStats();
        this.refreshCraftingInterfaces();
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    public int getCraftingMaxEnergyUsage() {
        return ECOEnergyProfile.craftingMaxEnergyUsage(
            this.getCraftingWorkerCount(),
            this.tier,
            this.craftingOverclocked,
            this.craftingActiveCooling);
    }

    public int getCraftingEffectiveOverclockTimes() {
        if (!this.craftingOverclocked) {
            return 0;
        }
        int overclockTimes = this.calculateCraftingOverclockTimes();
        if (!this.craftingActiveCooling) {
            return overclockTimes;
        }
        int coolingMaxOverclock = this.getCraftingCoolantMaxOverclock();
        return coolingMaxOverclock <= 0 ? 0 : Math.min(overclockTimes, coolingMaxOverclock);
    }

    public int getCraftingWorkBonusValue() {
        return Math.min(10 + this.getCraftingEffectiveOverclockTimes() * 10, 100);
    }

    public int getCraftingWorkPowerMultiplier() {
        return this.craftingOverclocked && !this.craftingActiveCooling
            ? ECOEnergyProfile.overclockedCraftingPowerMultiplier(this.tier)
            : 1;
    }

    public int getCraftingThreadCountPerWorker() {
        int multiplier = this.craftingOverclocked ? ECOEnergyProfile.overclockedCraftingQueueMultiplier(this.tier) : 1;
        return ECOCraftingCapacity.threadSlotsPerWorker(
            TileCraftingWorker.BASE_QUEUE_CAPACITY,
            multiplier,
            this.craftingOverclocked,
            this.getCraftingParallelCount() > 0);
    }

    public int getCraftingMaxInFlightCrafts() {
        CraftingHostStats stats = this.getCraftingHostStats();
        return ECOCraftingCapacity
            .maxInFlightCrafts(stats.parallelCount, stats.workerCount, this.getCraftingThreadCountPerWorker());
    }

    public int getCraftingCurrentBatchSlots() {
        return ECOCraftingCapacity
            .availableCraftSlots(this.getCraftingMaxInFlightCrafts(), this.getCraftingWorkQueueDepth());
    }

    public boolean consumeCraftingCoolantForWork(int craftCount) {
        if (!this.craftingActiveCooling) {
            return true;
        }
        this.refillCraftingCoolant(true);
        int amount = ECOEnergyProfile.CRAFTING_COOLANT_PER_CRAFT * Math.max(1, craftCount);
        int requiredOverclock = this.getCraftingEffectiveOverclockTimes();
        if (amount <= 0) {
            return true;
        }
        if (this.craftingCoolant < amount) {
            return false;
        }
        if (requiredOverclock > 0 && this.getCraftingCoolantMaxOverclock() < requiredOverclock) {
            return false;
        }
        this.craftingCoolant -= amount;
        if (this.craftingCoolant <= 0) {
            this.craftingCoolant = 0;
            this.craftingCoolantMaxOverclock = 0;
        }
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
        return true;
    }

    public int getCraftingCoolantCraftLimit(int requestedCrafts) {
        int normalized = Math.max(0, requestedCrafts);
        if (!this.craftingActiveCooling || normalized <= 0) {
            return normalized;
        }
        this.refillCraftingCoolant(true);
        int requiredOverclock = this.getCraftingEffectiveOverclockTimes();
        if (requiredOverclock > 0 && this.getCraftingCoolantMaxOverclock() < requiredOverclock) {
            return 0;
        }
        if (ECOEnergyProfile.CRAFTING_COOLANT_PER_CRAFT <= 0) {
            return normalized;
        }
        return Math.min(normalized, this.craftingCoolant / ECOEnergyProfile.CRAFTING_COOLANT_PER_CRAFT);
    }

    public double extractCraftingEnergy(double amount, boolean simulate) {
        if (amount <= 0D || this.subsystem != ECOControllerSubsystem.CRAFTING || this.worldObj == null) {
            return 0D;
        }

        CraftingMemberCache cache = this.getCraftingMemberCache();
        double remaining = amount;
        double extracted = 0D;

        for (TileECOInterface ecoInterface : cache.craftingInterfaces()) {
            if (!ecoInterface.isNetworkOnline()) {
                continue;
            }
            double fromInterface = ecoInterface.extractAEPower(remaining, simulate);
            extracted += fromInterface;
            remaining -= fromInterface;
            if (remaining <= 0D) {
                return amount;
            }
        }

        return extracted;
    }

    private int calculateCraftingOverclockTimes() {
        int threadCount = this.getCraftingParallelCount();
        int availableThreads = ECOEnergyProfile
            .craftingThreadCapacity(this.getCraftingWorkerCount(), this.tier, this.craftingOverclocked);
        return ECOCraftingCapacity.overclockTimes(threadCount, availableThreads);
    }

    public int getCraftingCoolantMaxOverclock() {
        if (!this.craftingActiveCooling || this.craftingCoolant <= 0 || this.worldObj == null) {
            return 0;
        }
        int maxOverclock = this.craftingCoolantMaxOverclock;
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileCraftingHatch hatch && hatch.isInput()) {
                FluidStack fluid = hatch.getFluidStack();
                if (fluid != null) {
                    ECOCoolingRecipe recipe = ECOCoolingRecipes.find(fluid, null);
                    if (recipe != null) {
                        maxOverclock = Math.max(maxOverclock, recipe.getMaxOverclock());
                    }
                }
            }
        }
        return maxOverclock;
    }

    private void refillCraftingCoolant() {
        this.refillCraftingCoolant(false);
    }

    private void refillCraftingCoolant(boolean allowNetworkFluid) {
        if (this.worldObj == null || this.worldObj.isRemote
            || this.subsystem != ECOControllerSubsystem.CRAFTING
            || !this.formed
            || !this.craftingActiveCooling
            || this.craftingCoolant >= MAX_CRAFTING_COOLANT) {
            return;
        }
        TileCraftingHatch outputHatch = this.firstCraftingFluidOutputHatch();
        if (this.refillCraftingCoolantFromHatches(outputHatch)) {
            return;
        }
        if (allowNetworkFluid) {
            this.refillCraftingCoolantFromNetwork(outputHatch);
        }
        if (this.craftingCoolant <= 0) {
            this.craftingCoolantMaxOverclock = 0;
        }
    }

    private boolean refillCraftingCoolantFromHatches(TileCraftingHatch outputHatch) {
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (!(tile instanceof TileCraftingHatch inputHatch) || !inputHatch.isInput()) {
                continue;
            }
            FluidStack input = inputHatch.getFluidStack();
            ECOCoolingRecipe recipe = ECOCoolingRecipes.find(input, null);
            if (recipe == null) {
                continue;
            }
            int recipes = Math.min(
                input.amount / recipe.getInputAmount(),
                (MAX_CRAFTING_COOLANT - this.craftingCoolant) / recipe.getCoolant());
            if (recipes <= 0) {
                continue;
            }
            FluidStack output = this.craftingCoolantOutput(recipe, recipes);
            if (this.cannotAcceptCraftingCoolantOutput(outputHatch, output)) {
                continue;
            }
            FluidStack drain = new FluidStack(input.getFluid(), recipes * recipe.getInputAmount());
            FluidStack drained = inputHatch.drainInputFluid(drain, true);
            if (drained == null || drained.amount <= 0) {
                continue;
            }
            int completed = drained.amount / recipe.getInputAmount();
            if (completed <= 0) {
                continue;
            }
            this.completeCraftingCoolantRefill(recipe, completed, outputHatch);
            return true;
        }
        return false;
    }

    private void refillCraftingCoolantFromNetwork(TileCraftingHatch outputHatch) {
        for (ECOCoolingRecipe recipe : this.networkCraftingCoolantRecipes()) {
            int maxRecipes = (MAX_CRAFTING_COOLANT - this.craftingCoolant) / recipe.getCoolant();
            if (maxRecipes <= 0) {
                return;
            }
            if (this.refillCraftingCoolantFromNetworkRecipe(recipe, maxRecipes, outputHatch)) {
                return;
            }
        }
    }

    private boolean refillCraftingCoolantFromNetworkRecipe(ECOCoolingRecipe recipe, int maxRecipes,
        TileCraftingHatch outputHatch) {
        World world = this.worldObj;
        if (world == null) {
            return false;
        }
        FluidStack request = new FluidStack(recipe.getInputFluid(), maxRecipes * recipe.getInputAmount());
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (!(tile instanceof TileECOInterface ecoInterface)) {
                continue;
            }
            if (ecoInterface.getSubsystem() != ECOControllerSubsystem.CRAFTING || !ecoInterface.isNetworkOnline()) {
                continue;
            }
            FluidStack simulated = ecoInterface.extractCraftingFluid(request, true);
            if (simulated == null || simulated.amount < recipe.getInputAmount()) {
                continue;
            }
            int availableRecipes = Math.min(maxRecipes, simulated.amount / recipe.getInputAmount());
            FluidStack output = this.craftingCoolantOutput(recipe, availableRecipes);
            if (this.cannotAcceptCraftingCoolantOutput(outputHatch, output)) {
                continue;
            }
            request.amount = availableRecipes * recipe.getInputAmount();
            FluidStack extracted = ecoInterface.extractCraftingFluid(request, false);
            if (extracted == null || extracted.amount < recipe.getInputAmount()) {
                continue;
            }
            int completed = extracted.amount / recipe.getInputAmount();
            this.completeCraftingCoolantRefill(recipe, completed, outputHatch);
            return true;
        }
        return false;
    }

    private List<ECOCoolingRecipe> networkCraftingCoolantRecipes() {
        List<ECOCoolingRecipe> recipes = new ArrayList<>(ECOCoolingRecipes.all());
        recipes.sort((left, right) -> {
            int overclock = right.getMaxOverclock() - left.getMaxOverclock();
            if (overclock != 0) {
                return overclock;
            }
            return right.getCoolant() - left.getCoolant();
        });
        return recipes;
    }

    private FluidStack craftingCoolantOutput(ECOCoolingRecipe recipe, int recipes) {
        FluidStack output = recipe.getOutput();
        if (output != null) {
            output.amount *= recipes;
        }
        return output;
    }

    private boolean cannotAcceptCraftingCoolantOutput(TileCraftingHatch outputHatch, FluidStack output) {
        return output != null && (outputHatch == null || outputHatch.fillOutputFluid(output, false) < output.amount);
    }

    private void completeCraftingCoolantRefill(ECOCoolingRecipe recipe, int recipes, TileCraftingHatch outputHatch) {
        FluidStack output = this.craftingCoolantOutput(recipe, recipes);
        if (output != null && outputHatch != null) {
            outputHatch.fillOutputFluid(output, true);
        }
        this.craftingCoolant = Math.min(MAX_CRAFTING_COOLANT, this.craftingCoolant + recipes * recipe.getCoolant());
        this.craftingCoolantMaxOverclock = Math.max(this.craftingCoolantMaxOverclock, recipe.getMaxOverclock());
    }

    private TileCraftingHatch firstCraftingFluidOutputHatch() {
        if (this.worldObj == null) {
            return null;
        }
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileCraftingHatch && !((TileCraftingHatch) tile).isInput()) {
                return (TileCraftingHatch) tile;
            }
        }
        return null;
    }

    public long getUsedComputationThreads() {
        return this.collectComputationRuntime().usedThreads;
    }

    public long getUsedComputationStorageBytes() {
        return this.collectComputationRuntime().usedStorageBytes;
    }

    public List<ComputationTaskInfo> getComputationTaskEntries() {
        return this.collectComputationRuntime().taskEntries;
    }

    public boolean hasActiveComputationTasks() {
        return this.collectComputationRuntime().usedThreads > 0L;
    }

    private ComputationRuntime collectComputationRuntime() {
        ComputationRuntime runtime = new ComputationRuntime();
        if (this.subsystem != ECOControllerSubsystem.COMPUTATION || this.worldObj == null) {
            return runtime;
        }
        Set<ECOFormationBlockPos> visitedInterfaces = new HashSet<>();
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            if (!visitedInterfaces.add(pos)) {
                continue;
            }
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileECOInterface ecoInterface) {
                if (ecoInterface.getSubsystem() == ECOControllerSubsystem.COMPUTATION) {
                    runtime.addThreads(ecoInterface.getComputationActiveThreads());
                    runtime.addStorageBytes(ecoInterface.getComputationUsedStorageBytes());
                    runtime.taskEntries.addAll(ecoInterface.getComputationTaskEntries());
                }
            }
        }
        return runtime;
    }

    public boolean isComputationHostActive() {
        return this.subsystem == ECOControllerSubsystem.COMPUTATION && this.formed && this.computationInterfaceOnline;
    }

    public void cycleComputationCpuSelectionMode() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return;
        }
        this.computationCpuSelectionMode = this.computationCpuSelectionMode.next();
        this.refreshComputationInterfaces();
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    public void onComputationHostCapacityChanged() {
        if (this.subsystem != ECOControllerSubsystem.COMPUTATION) {
            return;
        }
        this.computationHostStatsDirty = true;
        this.refreshComputationHostDisplayState(true);
        this.refreshComputationInterfaces();
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    public void onCraftingHostStateChanged() {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return;
        }
        this.refillCraftingCoolant();
        this.craftingHostStatsDirty = true;
        this.refreshCraftingHostStats();
        this.refreshCraftingInterfaces();
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    public void onCraftingMemberStateChanged() {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return;
        }
        this.craftingHostStatsDirty = true;
        this.markDirty();
    }

    public void onCraftingPatternsChanged() {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return;
        }
        this.craftingHostStatsDirty = true;
        this.refreshCraftingHostStats();
        this.refreshCraftingInterfaces();
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    public void onCraftingVirtualPoolStateChanged() {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return;
        }
        this.craftingHostStatsDirty = true;
        this.markDirty();
    }

    public void onHostDomainContentChanged() {
        this.markDirty();
        this.hostDomainClientUpdatePending = true;
    }

    public void onStorageInterfaceModeChanged() {
        if (this.subsystem != ECOControllerSubsystem.STORAGE) {
            return;
        }
        this.onStorageBackendChanged();
    }

    public boolean canExtractDriveCell(TileECODrive drive) {
        ItemStack stack = drive == null ? null : drive.getCellStack();
        return stack == null || !ECOStorageCellMetadata.hasNonPortableState(stack)
            && this.hostMode != ECOStorageHostMode.MIGRATING_TO_INFINITE
            && this.hostMode != ECOStorageHostMode.FORMED_INFINITE;
    }

    public boolean canAcceptDriveCell(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        if (!ECOStorageCellMetadata.hasNonPortableState(stack)) {
            return this.hostMode == ECOStorageHostMode.UNFORMED || this.hostMode == ECOStorageHostMode.FORMED_NORMAL;
        }
        UUID stackDomain = ECOStorageCellMetadata.getHostDomainId(stack);
        return this.hostDomainId != null && this.hostDomainId.equals(stackDomain)
            && (this.hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE
                || this.hostMode == ECOStorageHostMode.FORMED_INFINITE);
    }

    public int getInfiniteStorageComponentCount() {
        return isInfiniteStorageComponent(this.infiniteStorageComponent) ? this.infiniteStorageComponent.stackSize : 0;
    }

    public boolean canTakeInfiniteStorageComponent() {
        return !this.hasInfiniteModeState() || this.canExitInfiniteMode();
    }

    public boolean canRemoveFromWorld() {
        return !this.hasInfiniteModeState() || this.canExitInfiniteMode();
    }

    public boolean blocksWorldRemoval() {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return false;
        }
        if (!this.canRemoveFromWorld()) {
            return true;
        }
        if (this.hasInfiniteModeState()) {
            this.exitInfiniteMode();
        }
        return false;
    }

    public boolean protectsWorldPosition(int x, int y, int z) {
        if (this.worldObj != null && this.worldObj.isRemote) {
            return false;
        }
        return this.hasInfiniteModeState() && this.isFormedStructureBlock(x, y, z);
    }

    public String getLastFormationMessage() {
        return this.lastFormationMessage;
    }

    public ECOStorageDriveProvider createStorageDriveProvider() {
        return new ECOStorageDriveProvider(
            this.worldObj,
            this.formed ? this.formedMemberBlocks : new ArrayList<>(),
            this);
    }

    public int getStorageBackendRevision() {
        return this.storageBackendRevision;
    }

    public void onStorageBackendChanged() {
        this.storageBackendRevision++;
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    public ECOFormationResult scanFormation() {
        ECOFormationResult result = ECOFormationScanner.scan(this);
        this.applyFormationResult(result);
        return result;
    }

    private void applyFormationResult(ECOFormationResult result) {
        FormationChange change = this.calculateFormationChange(result);
        boolean preserveInfiniteMembers = this.hasInfiniteModeState() && !result.isFormed();
        this.lastFormationMessage = result.getMessage();
        if (!preserveInfiniteMembers) {
            this.mirrored = result.isMirrored();
            this.replaceHiddenBlocks(result.getHiddenBlocks());
            this.replaceFormedMemberBlocks(result.getFormedMemberBlocks(), change.mirroredChanged);
        } else {
            this.clearFormationVisibility();
        }
        this.setFormed(result.isFormed());
        this.refreshComputationHostDisplayState(change.stateChanged);
        this.refreshCraftingHostDisplayState(change.stateChanged);
        this.updateHostStorageState();
        this.syncFormationChange(change);
    }

    private FormationChange calculateFormationChange(ECOFormationResult result) {
        boolean mirroredChanged = this.mirrored != result.isMirrored();
        boolean stateChanged = this.formed != result.isFormed() || mirroredChanged
            || !samePositions(this.hiddenBlocks, result.getHiddenBlocks())
            || !samePositions(this.formedMemberBlocks, result.getFormedMemberBlocks());
        return new FormationChange(stateChanged, mirroredChanged);
    }

    private void syncFormationChange(FormationChange change) {
        if (change.stateChanged) {
            this.storageBackendRevision++;
            this.markDirty();
        }
        if (change.stateChanged && this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    private void replaceHiddenBlocks(List<ECOFormationBlockPos> newHiddenBlocks) {
        if (samePositions(this.hiddenBlocks, newHiddenBlocks)) {
            return;
        }
        if (this.worldObj != null) {
            ECOFormationVisibility.replace(this.worldObj, this.hiddenBlocks, newHiddenBlocks);
        }
        this.hiddenBlocks.clear();
        this.hiddenBlocks.addAll(newHiddenBlocks);
        this.invalidateCraftingMemberCache();
    }

    private void replaceFormedMemberBlocks(List<ECOFormationBlockPos> newFormedMemberBlocks) {
        this.replaceFormedMemberBlocks(newFormedMemberBlocks, false);
    }

    private void replaceFormedMemberBlocks(List<ECOFormationBlockPos> newFormedMemberBlocks, boolean force) {
        if (!force && samePositions(this.formedMemberBlocks, newFormedMemberBlocks)) {
            return;
        }
        if (this.worldObj != null) {
            ECOFormationVisibility
                .replaceFormedMembers(this.worldObj, this.formedMemberBlocks, newFormedMemberBlocks, this.mirrored);
        }
        this.formedMemberBlocks.clear();
        this.formedMemberBlocks.addAll(newFormedMemberBlocks);
        this.computationHostStatsDirty = true;
        this.craftingHostStatsDirty = true;
        this.invalidateCraftingMemberCache();
    }

    private void refreshComputationHostDisplayState(boolean forceStatsRefresh) {
        if (this.subsystem != ECOControllerSubsystem.COMPUTATION) {
            this.computationHostStats = ComputationHostStats.EMPTY;
            this.computationHostStatsDirty = false;
            this.computationInterfaceOnline = false;
            return;
        }
        if (forceStatsRefresh || this.computationHostStatsDirty) {
            this.refreshComputationHostStats();
        }
        this.refreshComputationInterfaceOnline();
    }

    private void refreshComputationHostStats() {
        if (this.subsystem != ECOControllerSubsystem.COMPUTATION || !this.formed) {
            this.computationHostStats = ComputationHostStats.EMPTY;
            this.computationHostStatsDirty = false;
            return;
        }
        this.computationHostStats = ComputationHostStats.create(this, this.formedMemberBlocks);
        this.computationHostStatsDirty = false;
    }

    private void refreshComputationInterfaceOnline() {
        if (this.subsystem != ECOControllerSubsystem.COMPUTATION || !this.formed) {
            this.computationInterfaceOnline = false;
            return;
        }
        this.computationInterfaceOnline = this.hasOnlineInterface(ECOControllerSubsystem.COMPUTATION);
    }

    private void refreshComputationInterfaces() {
        if (this.worldObj == null || this.subsystem != ECOControllerSubsystem.COMPUTATION) {
            return;
        }
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileECOInterface ecoInterface) {
                if (ecoInterface.getSubsystem() == ECOControllerSubsystem.COMPUTATION) {
                    ecoInterface.requestComputationCpuRefresh();
                }
            }
        }
    }

    private void refreshCraftingInterfaces() {
        if (this.worldObj == null || this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return;
        }
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileECOInterface ecoInterface) {
                if (ecoInterface.getSubsystem() == ECOControllerSubsystem.CRAFTING) {
                    ecoInterface.requestCraftingProviderRefresh();
                }
            }
        }
    }

    private void refreshCraftingHostDisplayState(boolean forceStatsRefresh) {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            this.craftingHostStats = CraftingHostStats.EMPTY;
            this.craftingHostStatsDirty = false;
            return;
        }
        if (forceStatsRefresh || this.craftingHostStatsDirty) {
            this.refreshCraftingHostStats();
        }
    }

    private void refreshCraftingHostStats() {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING || !this.formed) {
            this.craftingHostStats = CraftingHostStats.EMPTY;
            this.craftingHostStatsDirty = false;
            return;
        }
        CraftingMemberCache cache = this.getCraftingMemberCache();
        this.craftingHostStats = CraftingHostStats.fromCache(this, cache);
        this.craftingHostStatsDirty = false;
    }

    private void updateHostStorageState() {
        if (this.worldObj == null || this.worldObj.isRemote || this.subsystem != ECOControllerSubsystem.STORAGE) {
            return;
        }
        if (!this.formed) {
            if (this.hostMode == ECOStorageHostMode.UNFORMED || this.hostMode == ECOStorageHostMode.FORMED_NORMAL) {
                this.hostMode = ECOStorageHostMode.UNFORMED;
            }
            return;
        }
        if (this.hostMode == ECOStorageHostMode.UNFORMED) {
            this.hostMode = ECOStorageHostMode.FORMED_NORMAL;
            this.storageBackendRevision++;
            this.markDirty();
        }
        if (this.hostMode == ECOStorageHostMode.FORMED_NORMAL && this.isInfiniteUnlockConfigured()) {
            this.startInfiniteMigration();
        } else if (this.hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            this.resumeInfiniteMigration();
        }
    }

    private boolean isInfiniteUnlockConfigured() {
        return this.subsystem == ECOControllerSubsystem.STORAGE && this.tier == ECOControllerTier.L9
            && this.getInfiniteStorageComponentCount() >= REQUIRED_INFINITE_COMPONENTS
            && this.formedMemberBlocks.size() >= REQUIRED_INFINITE_DRIVES
            && this.areAllFormedDrivesL9Matrices();
    }

    private boolean areAllFormedDrivesL9Matrices() {
        if (this.worldObj == null || this.formedMemberBlocks.isEmpty()) {
            return false;
        }
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (!(tile instanceof TileECODrive)) {
                return false;
            }
            if (isNotL9StorageMatrix(((TileECODrive) tile).getCellStack())) {
                return false;
            }
        }
        return true;
    }

    private void startInfiniteMigration() {
        if (this.worldObj == null || this.worldObj.isRemote || !this.formed || !this.isInfiniteUnlockConfigured()) {
            return;
        }
        UUID domainId = this.hostDomainId == null ? UUID.randomUUID() : this.hostDomainId;
        List<UUID> diskIds = new ArrayList<>();
        Set<UUID> seenDisks = new HashSet<>();
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (!(tile instanceof TileECODrive)) {
                return;
            }
            ItemStack stack = ((TileECODrive) tile).getCellStack();
            if (!this.isMigrationCandidate(stack, domainId)) {
                return;
            }
            UUID diskId = ECOStorageCellMetadata.getOrCreateDiskId(stack);
            if (!seenDisks.add(diskId)) {
                return;
            }
            diskIds.add(diskId);
        }
        if (diskIds.isEmpty()) {
            return;
        }
        this.hostDomainId = domainId;
        this.hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
        this.storageBackendRevision++;
        this.memberDiskIds.clear();
        this.memberDiskIds.addAll(diskIds);
        this.migrationSteps.clear();
        for (UUID diskId : diskIds) {
            this.migrationSteps.put(diskId, MIGRATION_NOT_STARTED);
        }
        this.markDirty();
        this.resumeInfiniteMigration();
    }

    private void resumeInfiniteMigration() {
        if (this.worldObj == null || this.worldObj.isRemote
            || this.hostDomainId == null
            || this.hostMode != ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            return;
        }
        ECOStorageDomainData data = ECOStorageDomainData.get(this.worldObj);
        data.getOrCreateDomain(this.hostDomainId);
        for (int i = 0; i < this.memberDiskIds.size(); i++) {
            UUID diskId = this.memberDiskIds.get(i);
            TileECODrive drive = this.findDriveByDiskId(diskId);
            if (drive == null || drive.getCellStack() == null) {
                return;
            }
            ItemStack stack = drive.getCellStack();
            int step = this.migrationSteps.getOrDefault(diskId, MIGRATION_NOT_STARTED);
            if (step < MIGRATION_COPYING) {
                this.setMigrationStep(diskId, MIGRATION_COPYING);
                step = MIGRATION_COPYING;
            }
            if (step == MIGRATION_COPYING) {
                try {
                    ECOStorageBackend source = drive.getOrLoadCellBackend();
                    if (source == null) {
                        source = ECOStorageCellAccess.load(stack);
                    }
                    data.commitDiskToDomain(this.hostDomainId, diskId, source);
                    this.setMigrationStep(diskId, MIGRATION_SOURCE_CLEARED);
                    this.markDirty();
                    step = MIGRATION_SOURCE_CLEARED;
                } catch (Exception e) {
                    NeoECOAE.LOG.error("Migration failed for disk {}: {}", diskId, e.getMessage());
                    return;
                }
            }
            if (step == MIGRATION_SOURCE_CLEARED) {
                ECOStorageCellAccess.clearStorage(stack);
                drive.discardCellBackend();
                ECOStorageCellMetadata.markDomainMember(stack, this.hostDomainId, i);
                ECOStorageCellMetadata.writeSummary(stack, 0L, 0);
                drive.markDirty();
                this.setMigrationStep(diskId, MIGRATION_BOUND_AS_MEMBER);
            }
        }
        boolean complete = !this.memberDiskIds.isEmpty();
        for (UUID diskId : this.memberDiskIds) {
            if (this.migrationSteps.getOrDefault(diskId, MIGRATION_NOT_STARTED) != MIGRATION_BOUND_AS_MEMBER) {
                complete = false;
                break;
            }
        }
        if (complete) {
            this.hostMode = ECOStorageHostMode.FORMED_INFINITE;
            this.migrationSteps.clear();
            this.storageBackendRevision++;
            this.markDirty();
            if (this.worldObj != null) {
                this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
            }
        }
    }

    private void setMigrationStep(UUID diskId, int step) {
        this.migrationSteps.put(diskId, step);
        this.markDirty();
    }

    private boolean isMigrationCandidate(ItemStack stack, UUID domainId) {
        if (isNotL9StorageMatrix(stack)) {
            return false;
        }
        if (!ECOStorageCellMetadata.hasNonPortableState(stack)) {
            return true;
        }
        UUID stackDomain = ECOStorageCellMetadata.getHostDomainId(stack);
        return domainId.equals(stackDomain);
    }

    private TileECODrive findDriveByDiskId(UUID diskId) {
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            TileECODrive drive = this.getDriveAt(pos);
            if (drive != null && diskId.equals(ECOStorageCellMetadata.getDiskId(drive.getCellStack()))) {
                return drive;
            }
        }
        return null;
    }

    private TileECODrive getDriveAt(ECOFormationBlockPos pos) {
        if (this.worldObj == null || pos == null) {
            return null;
        }
        TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        return tile instanceof TileECODrive ? (TileECODrive) tile : null;
    }

    private boolean canExitInfiniteMode() {
        if (this.worldObj == null || this.hostDomainId == null
            || this.hostMode != ECOStorageHostMode.FORMED_INFINITE
            || !this.formed) {
            return false;
        }
        if (!ECOStorageDomainData.get(this.worldObj)
            .isDomainEmpty(this.hostDomainId)) {
            return false;
        }
        for (UUID diskId : this.memberDiskIds) {
            if (this.findDriveByDiskId(diskId) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean hasInfiniteModeState() {
        return this.hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE
            || this.hostMode == ECOStorageHostMode.FORMED_INFINITE
            || this.hostDomainId != null
            || !this.memberDiskIds.isEmpty()
            || !this.migrationSteps.isEmpty();
    }

    private boolean isFormedStructureBlock(int x, int y, int z) {
        if (this.xCoord == x && this.yCoord == y && this.zCoord == z) {
            return true;
        }
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    private void exitInfiniteMode() {
        if (!this.canExitInfiniteMode()) {
            return;
        }
        ECOStorageDomainData data = ECOStorageDomainData.get(this.worldObj);
        UUID exitingDomainId = this.hostDomainId;
        for (UUID diskId : new ArrayList<>(this.memberDiskIds)) {
            TileECODrive drive = this.findDriveByDiskId(diskId);
            if (drive != null && drive.getCellStack() != null) {
                data.forgetCommittedSource(exitingDomainId, diskId);
                ECOStorageCellMetadata.clearDomainBinding(drive.getCellStack());
                drive.markDirty();
            }
        }
        data.removeDomain(exitingDomainId);
        this.hostDomainId = null;
        this.memberDiskIds.clear();
        this.migrationSteps.clear();
        this.hostMode = this.formed ? ECOStorageHostMode.FORMED_NORMAL : ECOStorageHostMode.UNFORMED;
        this.storageBackendRevision++;
        this.markDirty();
    }

    private void onInfiniteComponentChanged() {
        this.markDirty();
        this.updateHostStorageState();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    private CraftingMemberCache getCraftingMemberCache() {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING || !this.formed) {
            return CraftingMemberCache.EMPTY;
        }

        int currentRevision = this.craftingMemberCacheRevision;
        if (!this.craftingMemberCacheDirty && this.craftingMemberCache.isValid(currentRevision)) {
            return this.craftingMemberCache;
        }

        this.craftingMemberCache = CraftingMemberCache.build(this, this.formedMemberBlocks, this.hiddenBlocks);
        this.craftingMemberCacheDirty = false;
        return this.craftingMemberCache;
    }

    private void invalidateCraftingMemberCache() {
        this.craftingMemberCacheDirty = true;
        this.craftingMemberCacheRevision++;
    }

    int getCraftingMemberCacheRevision() {
        return this.craftingMemberCacheRevision;
    }

    private static boolean isInfiniteStorageComponent(ItemStack stack) {
        return stack != null && stack.getItem() == NEStorageItems.ecoInfiniteCellComponent;
    }

    private static boolean isNotL9StorageMatrix(ItemStack stack) {
        return stack == null || !"256M".equals(
            ECOStorageCellAccess.readTier(
                stack,
                stack.getItem() instanceof NEStorageItems.ECOStorageCellItem
                    ? ((NEStorageItems.ECOStorageCellItem) stack.getItem()).getTier()
                    : ""));
    }

    public ModelFacing getFacing() {
        return ModelFacing.fromMeta(this.facingMeta);
    }

    public void setFacingMeta(int facingMeta) {
        this.facingMeta = facingMeta & 3;
        this.markDirty();
    }

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? this.infiniteStorageComponent : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot != 0 || this.infiniteStorageComponent == null || amount <= 0) {
            return null;
        }
        if (!this.canTakeInfiniteStorageComponent()) {
            return null;
        }
        boolean shouldExitInfiniteMode = (this.worldObj == null || !this.worldObj.isRemote)
            && this.hasInfiniteModeState();
        ItemStack removed;
        if (this.infiniteStorageComponent.stackSize <= amount) {
            removed = this.infiniteStorageComponent.copy();
            this.infiniteStorageComponent = null;
        } else {
            removed = this.infiniteStorageComponent.splitStack(amount);
            if (this.infiniteStorageComponent.stackSize <= 0) {
                this.infiniteStorageComponent = null;
            }
        }
        if (shouldExitInfiniteMode) {
            this.exitInfiniteMode();
        }
        this.onInfiniteComponentChanged();
        return removed;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot != 0 || this.infiniteStorageComponent == null) {
            return null;
        }
        if (!this.canTakeInfiniteStorageComponent()) {
            return null;
        }
        boolean shouldExitInfiniteMode = (this.worldObj == null || !this.worldObj.isRemote)
            && this.hasInfiniteModeState();
        ItemStack stack = this.infiniteStorageComponent.copy();
        this.infiniteStorageComponent = null;
        if (shouldExitInfiniteMode) {
            this.exitInfiniteMode();
        }
        this.onInfiniteComponentChanged();
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        boolean removingInfiniteComponent = stack == null && this.infiniteStorageComponent != null
            && this.hasInfiniteModeState();
        if (removingInfiniteComponent && !this.canExitInfiniteMode()) {
            return;
        }
        this.infiniteStorageComponent = stack;
        if (this.infiniteStorageComponent != null
            && this.infiniteStorageComponent.stackSize > this.getInventoryStackLimit()) {
            this.infiniteStorageComponent.stackSize = this.getInventoryStackLimit();
        }
        if (removingInfiniteComponent) {
            this.exitInfiniteMode();
        }
        this.onInfiniteComponentChanged();
    }

    @Override
    public String getInventoryName() {
        return "container.neoecoae.storage_infinite_component";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return REQUIRED_INFINITE_COMPONENTS;
    }

    @Override
    public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        return this.worldObj != null && this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
            && player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && isInfiniteStorageComponent(stack);
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        if (this.hostDomainClientUpdatePending) {
            this.hostDomainClientUpdatePending = false;
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
        if (this.worldObj.getTotalWorldTime() % 20L == 0L) {
            this.scanFormation();
        } else if (this.hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            this.resumeInfiniteMigration();
        }
        if (this.subsystem == ECOControllerSubsystem.STORAGE && this.formed
            && this.hostMode != ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            TileECOInterface storageInterface = this.getStorageInterfaceForTransfer();
            if (storageInterface != null && storageInterface.isStorageTransferMode()) {
                ECOStorageInterfaceTransfer.transfer(storageInterface, this);
            }
        }
        if (this.worldObj.getTotalWorldTime() % CRAFTING_OUTPUT_DRAIN_INTERVAL == 0L) {
            this.drainCraftingOutputHatchesToNetwork();
        }
        if (this.subsystem == ECOControllerSubsystem.CRAFTING && this.formed) {
            this.craftingVirtualPool.tick(this);
        }
    }

    @Override
    public void invalidate() {
        ECOControllerRegistry.unregister(this);
        this.clearFormationVisibility();
        this.invalidateCraftingMemberCache();
        this.craftingMemberCache = CraftingMemberCache.EMPTY;
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        ECOControllerRegistry.unregister(this);
        this.clearFormationVisibility();
        this.invalidateCraftingMemberCache();
        super.onChunkUnload();
    }

    @Override
    public void validate() {
        super.validate();
        ECOControllerRegistry.register(this);
    }

    private void clearFormationVisibility() {
        if (this.worldObj == null) {
            return;
        }
        ECOFormationVisibility.replace(this.worldObj, this.hiddenBlocks, new ArrayList<>());
        ECOFormationVisibility
            .replaceFormedMembers(this.worldObj, this.formedMemberBlocks, new ArrayList<>(), this.mirrored);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString(TAG_SUBSYSTEM, this.subsystem.getId());
        tag.setString(TAG_TIER, this.tier.getId());
        tag.setBoolean(TAG_FORMED, this.formed);
        tag.setBoolean(TAG_MIRRORED, this.mirrored);
        tag.setInteger(TAG_FACING_META, this.facingMeta);
        tag.setString(TAG_HOST_MODE, this.hostMode.getId());
        tag.setInteger(TAG_PRIORITY, this.priority);
        tag.setInteger(TAG_COMPUTATION_CPU_MODE, this.computationCpuSelectionMode.ordinal());
        tag.setBoolean(TAG_CRAFTING_OVERCLOCKED, this.craftingOverclocked);
        tag.setBoolean(TAG_CRAFTING_ACTIVE_COOLING, this.craftingActiveCooling);
        tag.setInteger(TAG_CRAFTING_COOLANT, this.getCraftingCoolant());
        tag.setInteger(TAG_CRAFTING_COOLANT_MAX_OVERCLOCK, this.craftingCoolantMaxOverclock);
        tag.setInteger(TAG_CRAFTING_PLANNER_ACCEPTED, this.craftingPlannerAccepted);
        tag.setInteger(TAG_CRAFTING_PLANNER_REJECTED, this.craftingPlannerRejected);
        CraftingHostStats craftingStats = this.getCraftingHostStats();
        tag.setInteger(TAG_CRAFTING_PATTERN_COUNT, craftingStats.patternCount);
        tag.setInteger(TAG_CRAFTING_PATTERN_BUS_COUNT, craftingStats.patternBusCount);
        tag.setInteger(TAG_CRAFTING_WORKER_COUNT, craftingStats.workerCount);
        tag.setInteger(TAG_CRAFTING_RUNNING_WORKER_COUNT, craftingStats.runningWorkerCount);
        tag.setInteger(TAG_CRAFTING_PARALLEL_COUNT, craftingStats.parallelCount);
        tag.setInteger(TAG_CRAFTING_PARALLEL_CORE_COUNT, craftingStats.parallelCoreCount);
        tag.setInteger(TAG_CRAFTING_INPUT_CACHED_ITEMS, craftingStats.inputCachedItems);
        tag.setInteger(TAG_CRAFTING_OUTPUT_CACHED_ITEMS, craftingStats.outputCachedItems);
        tag.setInteger(TAG_CRAFTING_OCCUPIED_CACHE_SLOTS, craftingStats.occupiedCacheSlots);
        NBTTagCompound virtualPoolTag = new NBTTagCompound();
        this.craftingVirtualPool.writeToNBT(virtualPoolTag);
        tag.setTag(TAG_CRAFTING_VIRTUAL_POOL, virtualPoolTag);
        if (this.hostDomainId != null) {
            tag.setString(TAG_HOST_DOMAIN_ID, this.hostDomainId.toString());
        }
        if (this.infiniteStorageComponent != null) {
            NBTTagCompound componentTag = new NBTTagCompound();
            this.infiniteStorageComponent.writeToNBT(componentTag);
            tag.setTag(TAG_INFINITE_COMPONENT, componentTag);
        }
        NBTTagList hiddenTag = new NBTTagList();
        for (ECOFormationBlockPos pos : this.hiddenBlocks) {
            hiddenTag.appendTag(writePos(pos));
        }
        tag.setTag(TAG_HIDDEN_BLOCKS, hiddenTag);
        NBTTagList formedMemberTag = new NBTTagList();
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            formedMemberTag.appendTag(writePos(pos));
        }
        tag.setTag(TAG_FORMED_MEMBER_BLOCKS, formedMemberTag);
        NBTTagList memberDiskTag = new NBTTagList();
        for (UUID diskId : this.memberDiskIds) {
            NBTTagCompound diskTag = new NBTTagCompound();
            diskTag.setString(TAG_DISK_ID, diskId.toString());
            memberDiskTag.appendTag(diskTag);
        }
        tag.setTag(TAG_MEMBER_DISKS, memberDiskTag);
        NBTTagList migrationTag = new NBTTagList();
        for (Map.Entry<UUID, Integer> entry : this.migrationSteps.entrySet()) {
            NBTTagCompound stepTag = new NBTTagCompound();
            stepTag.setString(
                TAG_DISK_ID,
                entry.getKey()
                    .toString());
            stepTag.setInteger(TAG_STEP, entry.getValue());
            migrationTag.appendTag(stepTag);
        }
        tag.setTag(TAG_MIGRATION_STEPS, migrationTag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.subsystem = ECOControllerSubsystem.fromId(tag.getString(TAG_SUBSYSTEM));
        this.tier = ECOControllerTier.fromId(tag.getString(TAG_TIER));
        this.formed = tag.getBoolean(TAG_FORMED);
        this.mirrored = tag.getBoolean(TAG_MIRRORED);
        this.facingMeta = tag.getInteger(TAG_FACING_META) & 3;
        this.hostMode = ECOStorageHostMode.fromId(tag.getString(TAG_HOST_MODE));
        this.priority = tag.getInteger(TAG_PRIORITY);
        this.computationCpuSelectionMode = ComputationCpuSelectionMode
            .fromOrdinal(tag.getInteger(TAG_COMPUTATION_CPU_MODE));
        this.craftingOverclocked = tag.getBoolean(TAG_CRAFTING_OVERCLOCKED);
        this.craftingActiveCooling = tag.getBoolean(TAG_CRAFTING_ACTIVE_COOLING);
        this.craftingCoolant = Math.max(0, Math.min(MAX_CRAFTING_COOLANT, tag.getInteger(TAG_CRAFTING_COOLANT)));
        this.craftingCoolantMaxOverclock = Math.max(0, tag.getInteger(TAG_CRAFTING_COOLANT_MAX_OVERCLOCK));
        this.craftingPlannerAccepted = Math.max(0, tag.getInteger(TAG_CRAFTING_PLANNER_ACCEPTED));
        this.craftingPlannerRejected = Math.max(0, tag.getInteger(TAG_CRAFTING_PLANNER_REJECTED));
        this.craftingVirtualPool.readFromNBT(
            tag.hasKey(TAG_CRAFTING_VIRTUAL_POOL, Constants.NBT.TAG_COMPOUND)
                ? tag.getCompoundTag(TAG_CRAFTING_VIRTUAL_POOL)
                : new NBTTagCompound(),
            this);
        this.craftingHostStats = CraftingHostStats.fromSaved(
            tag.getInteger(TAG_CRAFTING_PATTERN_COUNT),
            tag.getInteger(TAG_CRAFTING_PATTERN_BUS_COUNT),
            tag.getInteger(TAG_CRAFTING_WORKER_COUNT),
            tag.getInteger(TAG_CRAFTING_RUNNING_WORKER_COUNT),
            tag.getInteger(TAG_CRAFTING_PARALLEL_COUNT),
            tag.getInteger(TAG_CRAFTING_PARALLEL_CORE_COUNT),
            tag.getInteger(TAG_CRAFTING_INPUT_CACHED_ITEMS),
            tag.getInteger(TAG_CRAFTING_OUTPUT_CACHED_ITEMS),
            tag.getInteger(TAG_CRAFTING_OCCUPIED_CACHE_SLOTS));
        this.craftingHostStatsDirty = this.subsystem == ECOControllerSubsystem.CRAFTING && this.formed;
        this.hostDomainId = tag.hasKey(TAG_HOST_DOMAIN_ID) ? readUuid(tag.getString(TAG_HOST_DOMAIN_ID)) : null;
        this.infiniteStorageComponent = tag.hasKey(TAG_INFINITE_COMPONENT)
            ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag(TAG_INFINITE_COMPONENT))
            : null;
        this.replaceHiddenBlocks(readPositions(tag.getTagList(TAG_HIDDEN_BLOCKS, 10)));
        this.replaceFormedMemberBlocks(readPositions(tag.getTagList(TAG_FORMED_MEMBER_BLOCKS, 10)));
        this.memberDiskIds.clear();
        NBTTagList memberDiskTag = tag.getTagList(TAG_MEMBER_DISKS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < memberDiskTag.tagCount(); i++) {
            UUID diskId = readUuid(
                memberDiskTag.getCompoundTagAt(i)
                    .getString(TAG_DISK_ID));
            if (diskId != null) {
                this.memberDiskIds.add(diskId);
            }
        }
        this.migrationSteps.clear();
        NBTTagList migrationTag = tag.getTagList(TAG_MIGRATION_STEPS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < migrationTag.tagCount(); i++) {
            NBTTagCompound stepTag = migrationTag.getCompoundTagAt(i);
            UUID diskId = readUuid(stepTag.getString(TAG_DISK_ID));
            if (diskId != null) {
                this.migrationSteps.put(diskId, stepTag.getInteger(TAG_STEP));
            }
        }
        if (this.worldObj != null) {
            this.worldObj.markBlockRangeForRenderUpdate(
                this.xCoord - 16,
                this.yCoord - 3,
                this.zCoord - 16,
                this.xCoord + 16,
                this.yCoord + 3,
                this.zCoord + 16);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        tag.removeTag(TAG_CRAFTING_VIRTUAL_POOL);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    private static boolean samePositions(List<ECOFormationBlockPos> left, List<ECOFormationBlockPos> right) {
        Set<ECOFormationBlockPos> leftSet = new HashSet<>(left);
        Set<ECOFormationBlockPos> rightSet = new HashSet<>(right);
        return leftSet.equals(rightSet);
    }

    private static final class ComputationRuntime {

        private long usedThreads;
        private long usedStorageBytes;
        private final List<ComputationTaskInfo> taskEntries = new ArrayList<>();

        private void addThreads(long value) {
            this.usedThreads = saturatingAdd(this.usedThreads, value);
        }

        private void addStorageBytes(long value) {
            this.usedStorageBytes = saturatingAdd(this.usedStorageBytes, value);
        }
    }

    private static final class FormationChange {

        private final boolean stateChanged;
        private final boolean mirroredChanged;

        private FormationChange(boolean stateChanged, boolean mirroredChanged) {
            this.stateChanged = stateChanged;
            this.mirroredChanged = mirroredChanged;
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static int saturatedIncrement(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.readFromNBT(packet.func_148857_g());
    }

    private static NBTTagCompound writePos(ECOFormationBlockPos pos) {
        NBTTagCompound posTag = new NBTTagCompound();
        posTag.setInteger(TAG_X, pos.getX());
        posTag.setInteger(TAG_Y, pos.getY());
        posTag.setInteger(TAG_Z, pos.getZ());
        if (pos.getTier() != null) {
            posTag.setString(
                TAG_MEMBER_TIER,
                pos.getTier()
                    .getId());
        }
        return posTag;
    }

    private static List<ECOFormationBlockPos> readPositions(NBTTagList positionsTag) {
        List<ECOFormationBlockPos> positions = new ArrayList<>();
        for (int i = 0; i < positionsTag.tagCount(); i++) {
            NBTTagCompound posTag = positionsTag.getCompoundTagAt(i);
            String tierId = posTag.hasKey(TAG_MEMBER_TIER) ? posTag.getString(TAG_MEMBER_TIER) : "";
            ECOControllerTier tier = !tierId.isEmpty() ? ECOControllerTier.fromId(tierId) : null;
            positions.add(
                new ECOFormationBlockPos(
                    posTag.getInteger(TAG_X),
                    posTag.getInteger(TAG_Y),
                    posTag.getInteger(TAG_Z),
                    tier));
        }
        return positions;
    }

    private static UUID readUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
