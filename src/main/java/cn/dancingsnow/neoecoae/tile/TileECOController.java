package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
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
import net.minecraftforge.common.util.Constants;

import cn.dancingsnow.neoecoae.all.NEStorageItems;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationResult;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationScanner;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cn.dancingsnow.neoecoae.storage.ae2.ECOStorageDriveProvider;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageDomainData;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata;

public class TileECOController extends TileEntity implements IInventory {

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
    private static final String TAG_DISK_ID = "DiskId";
    private static final String TAG_STEP = "Step";
    private static final int REQUIRED_INFINITE_COMPONENTS = 64;
    private static final int REQUIRED_INFINITE_DRIVES = 16;
    private static final int MIGRATION_NOT_STARTED = 0;
    private static final int MIGRATION_COPYING = 1;
    private static final int MIGRATION_SOURCE_CLEARED = 2;
    private static final int MIGRATION_BOUND_AS_MEMBER = 3;

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
    private final List<UUID> memberDiskIds = new ArrayList<UUID>();
    private final Map<UUID, Integer> migrationSteps = new LinkedHashMap<UUID, Integer>();

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
        return new ArrayList<ECOFormationBlockPos>(this.formedMemberBlocks);
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

    public boolean isInfiniteStorageUnlocked() {
        return this.canUseHostDomainStorage();
    }

    public void onHostDomainContentChanged() {
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
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

    public String getLastFormationMessage() {
        return this.lastFormationMessage;
    }

    public ECOStorageDriveProvider createStorageDriveProvider() {
        return new ECOStorageDriveProvider(this.worldObj, this.formed ? this.formedMemberBlocks : new ArrayList<ECOFormationBlockPos>(), this);
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
        this.lastFormationMessage = result.getMessage();
        this.mirrored = result.isMirrored();
        this.replaceHiddenBlocks(result.getHiddenBlocks());
        this.replaceFormedMemberBlocks(result.getFormedMemberBlocks(), change.mirroredChanged);
        this.setFormed(result.isFormed());
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
        return this.subsystem == ECOControllerSubsystem.STORAGE
            && this.tier == ECOControllerTier.L9
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
            if (!isL9StorageMatrix(((TileECODrive) tile).getCellStack())) {
                return false;
            }
        }
        return true;
    }

    private boolean startInfiniteMigration() {
        if (this.worldObj == null || this.worldObj.isRemote || !this.formed || !this.isInfiniteUnlockConfigured()) {
            return false;
        }
        UUID domainId = this.hostDomainId == null ? UUID.randomUUID() : this.hostDomainId;
        List<UUID> diskIds = new ArrayList<UUID>();
        Set<UUID> seenDisks = new HashSet<UUID>();
        for (ECOFormationBlockPos pos : this.formedMemberBlocks) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (!(tile instanceof TileECODrive)) {
                return false;
            }
            ItemStack stack = ((TileECODrive) tile).getCellStack();
            if (!this.isMigrationCandidate(stack, domainId)) {
                return false;
            }
            UUID diskId = ECOStorageCellMetadata.getOrCreateDiskId(stack);
            if (!seenDisks.add(diskId)) {
                return false;
            }
            diskIds.add(diskId);
        }
        if (diskIds.isEmpty()) {
            return false;
        }
        this.hostDomainId = domainId;
        this.hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
        this.storageBackendRevision++;
        this.memberDiskIds.clear();
        this.memberDiskIds.addAll(diskIds);
        this.migrationSteps.clear();
        for (UUID diskId : diskIds) {
            this.migrationSteps.put(diskId, Integer.valueOf(MIGRATION_NOT_STARTED));
        }
        this.markDirty();
        this.resumeInfiniteMigration();
        return true;
    }

    private boolean resumeInfiniteMigration() {
        if (this.worldObj == null || this.worldObj.isRemote || this.hostDomainId == null
            || this.hostMode != ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            return false;
        }
        ECOStorageDomainData data = ECOStorageDomainData.get(this.worldObj);
        data.getOrCreateDomain(this.hostDomainId);
        boolean changed = false;
        for (int i = 0; i < this.memberDiskIds.size(); i++) {
            UUID diskId = this.memberDiskIds.get(i);
            TileECODrive drive = this.findDriveByDiskId(diskId);
            if (drive == null || drive.getCellStack() == null) {
                return changed;
            }
            ItemStack stack = drive.getCellStack();
            int step = this.migrationSteps.containsKey(diskId) ? this.migrationSteps.get(diskId).intValue() : MIGRATION_NOT_STARTED;
            if (step < MIGRATION_COPYING) {
                this.setMigrationStep(diskId, MIGRATION_COPYING);
                changed = true;
                step = MIGRATION_COPYING;
            }
            if (step == MIGRATION_COPYING) {
                ECOStorageBackend source = ECOStorageCellAccess.load(stack);
                data.commitDiskToDomain(this.hostDomainId, diskId, source);
                this.setMigrationStep(diskId, MIGRATION_SOURCE_CLEARED);
                changed = true;
                step = MIGRATION_SOURCE_CLEARED;
            }
            if (step == MIGRATION_SOURCE_CLEARED) {
                ECOStorageCellAccess.clearStorage(stack);
                ECOStorageCellMetadata.markDomainMember(stack, this.hostDomainId, i);
                ECOStorageCellMetadata.writeSummary(stack, 0L, 0);
                drive.markDirty();
                this.setMigrationStep(diskId, MIGRATION_BOUND_AS_MEMBER);
                changed = true;
            }
        }
        boolean complete = !this.memberDiskIds.isEmpty();
        for (UUID diskId : this.memberDiskIds) {
            if (!this.migrationSteps.containsKey(diskId)
                || this.migrationSteps.get(diskId).intValue() != MIGRATION_BOUND_AS_MEMBER) {
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
            changed = true;
        }
        return changed;
    }

    private void setMigrationStep(UUID diskId, int step) {
        this.migrationSteps.put(diskId, Integer.valueOf(step));
        this.markDirty();
    }

    private boolean isMigrationCandidate(ItemStack stack, UUID domainId) {
        if (!isL9StorageMatrix(stack)) {
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
        if (this.worldObj == null || this.hostDomainId == null || this.hostMode != ECOStorageHostMode.FORMED_INFINITE
            || !this.formed) {
            return false;
        }
        if (!ECOStorageDomainData.get(this.worldObj).isDomainEmpty(this.hostDomainId)) {
            return false;
        }
        for (UUID diskId : this.memberDiskIds) {
            if (this.findDriveByDiskId(diskId) == null) {
                return false;
            }
        }
        return true;
    }

    private void exitInfiniteMode() {
        if (!this.canExitInfiniteMode()) {
            return;
        }
        for (UUID diskId : new ArrayList<UUID>(this.memberDiskIds)) {
            TileECODrive drive = this.findDriveByDiskId(diskId);
            if (drive != null && drive.getCellStack() != null) {
                ECOStorageCellMetadata.clearDomainBinding(drive.getCellStack());
                drive.markDirty();
            }
        }
        ECOStorageDomainData.get(this.worldObj).removeDomain(this.hostDomainId);
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

    private static boolean isInfiniteStorageComponent(ItemStack stack) {
        return stack != null && stack.getItem() == NEStorageItems.ecoInfiniteCellComponent;
    }

    private static boolean isL9StorageMatrix(ItemStack stack) {
        return stack != null && "256M".equals(ECOStorageCellAccess.readTier(stack, stack.getItem() instanceof NEStorageItems.ECOStorageCellItem
            ? ((NEStorageItems.ECOStorageCellItem) stack.getItem()).getTier()
            : ""));
    }

    public int getFacingMeta() {
        return this.facingMeta;
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
        if (this.isInfiniteStorageUnlocked() && !this.canExitInfiniteMode()) {
            return null;
        }
        if (this.isInfiniteStorageUnlocked()) {
            this.exitInfiniteMode();
        }
        ItemStack removed;
        if (this.infiniteStorageComponent.stackSize <= amount) {
            removed = this.infiniteStorageComponent;
            this.infiniteStorageComponent = null;
        } else {
            removed = this.infiniteStorageComponent.splitStack(amount);
            if (this.infiniteStorageComponent.stackSize <= 0) {
                this.infiniteStorageComponent = null;
            }
        }
        this.onInfiniteComponentChanged();
        return removed;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot != 0) {
            return null;
        }
        ItemStack stack = this.infiniteStorageComponent;
        this.infiniteStorageComponent = null;
        if (stack != null) {
            this.onInfiniteComponentChanged();
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        this.infiniteStorageComponent = stack;
        if (this.infiniteStorageComponent != null && this.infiniteStorageComponent.stackSize > this.getInventoryStackLimit()) {
            this.infiniteStorageComponent.stackSize = this.getInventoryStackLimit();
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
        if (this.worldObj.getTotalWorldTime() % 20L == 0L) {
            this.scanFormation();
        } else if (this.hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            this.resumeInfiniteMigration();
        }
    }

    @Override
    public void invalidate() {
        this.replaceHiddenBlocks(new ArrayList<>());
        this.replaceFormedMemberBlocks(new ArrayList<>());
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        this.replaceHiddenBlocks(new ArrayList<>());
        this.replaceFormedMemberBlocks(new ArrayList<>());
        super.onChunkUnload();
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
            stepTag.setString(TAG_DISK_ID, entry.getKey().toString());
            stepTag.setInteger(TAG_STEP, entry.getValue().intValue());
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
        this.hostDomainId = tag.hasKey(TAG_HOST_DOMAIN_ID) ? readUuid(tag.getString(TAG_HOST_DOMAIN_ID)) : null;
        this.infiniteStorageComponent = tag.hasKey(TAG_INFINITE_COMPONENT)
            ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag(TAG_INFINITE_COMPONENT))
            : null;
        this.replaceHiddenBlocks(readPositions(tag.getTagList(TAG_HIDDEN_BLOCKS, 10)));
        this.replaceFormedMemberBlocks(readPositions(tag.getTagList(TAG_FORMED_MEMBER_BLOCKS, 10)));
        this.memberDiskIds.clear();
        NBTTagList memberDiskTag = tag.getTagList(TAG_MEMBER_DISKS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < memberDiskTag.tagCount(); i++) {
            UUID diskId = readUuid(memberDiskTag.getCompoundTagAt(i).getString(TAG_DISK_ID));
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
                this.migrationSteps.put(diskId, Integer.valueOf(stepTag.getInteger(TAG_STEP)));
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
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    private static boolean samePositions(List<ECOFormationBlockPos> left, List<ECOFormationBlockPos> right) {
        Set<ECOFormationBlockPos> leftSet = new HashSet<ECOFormationBlockPos>(left);
        Set<ECOFormationBlockPos> rightSet = new HashSet<ECOFormationBlockPos>(right);
        return leftSet.equals(rightSet);
    }

    private static final class FormationChange {

        private final boolean stateChanged;
        private final boolean mirroredChanged;

        private FormationChange(boolean stateChanged, boolean mirroredChanged) {
            this.stateChanged = stateChanged;
            this.mirroredChanged = mirroredChanged;
        }
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
