package cn.dancingsnow.neoecoae.tile;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.block.BlockECOInterface;
import cn.dancingsnow.neoecoae.computation.ComputationTaskInfo;
import cn.dancingsnow.neoecoae.computation.ae2.ECOComputationCpuPool;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;

public class TileECOInterface extends TileEntity implements IGridProxyable, IActionHost, IPriorityHost {

    private static final String TAG_SUBSYSTEM = "Subsystem";
    private static final String TAG_COMPUTATION_CPU_POOL = "ComputationCpuPool";
    private ECOControllerSubsystem subsystem = ECOControllerSubsystem.STORAGE;
    private final AENetworkProxy proxy;
    private IStorageGrid registeredStorageGrid;
    private ICellProvider registeredCellProvider;
    private TileECOController cachedController;
    private int registeredControllerRevision = -1;
    private boolean networkReady;
    private boolean computationCpuRefreshQueued;
    private long computationCpuRefreshQueuedTick;
    private final ECOComputationCpuPool computationCpuPool = new ECOComputationCpuPool(this);

    public TileECOInterface() {
        this(ECOControllerSubsystem.STORAGE);
    }

    public TileECOInterface(ECOControllerSubsystem subsystem) {
        this.subsystem = subsystem == null ? ECOControllerSubsystem.STORAGE : subsystem;
        this.proxy = new AENetworkProxy(this, "proxy", this.interfaceStack(), true);
        this.proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        this.proxy.setIdlePowerUsage(1.0D);
        this.proxy.setValidSides(EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN)));
    }

    public ECOControllerSubsystem getSubsystem() {
        return this.subsystem;
    }

    public TileECOController getBoundController() {
        return this.subsystem == ECOControllerSubsystem.STORAGE ? this.findController() : null;
    }

    @Override
    public int getPriority() {
        TileECOController controller = this.getBoundController();
        return controller == null ? 0 : controller.getPriority();
    }

    @Override
    public void setPriority(int newValue) {
        TileECOController controller = this.getBoundController();
        if (controller != null) {
            controller.setPriority(newValue);
        }
    }

    public boolean isNetworkOnline() {
        IGridNode node = this.proxy.getNode();
        return this.registeredStorageGrid != null && this.registeredCellProvider != null
            || node != null && node.isActive();
    }

    public boolean isComputationCpuOnline() {
        return this.subsystem == ECOControllerSubsystem.COMPUTATION && this.isNetworkOnline()
            && this.findController() != null;
    }

    long getComputationActiveThreads() {
        return this.computationCpuPool.activeThreadCount();
    }

    long getComputationUsedStorageBytes() {
        return this.computationCpuPool.usedStorageBytes();
    }

    List<ComputationTaskInfo> getComputationTaskEntries() {
        return this.computationCpuPool.taskEntries();
    }

    public void requestComputationCpuRefresh() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        this.computationCpuRefreshQueued = true;
        this.computationCpuRefreshQueuedTick = this.worldObj.getTotalWorldTime();
    }

    @Override
    public AENetworkProxy getProxy() {
        return this.proxy;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection direction) {
        return this.proxy.getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return this.proxy.getNode();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection direction) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {}

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {
        this.refreshSubsystemRegistration();
    }

    @Override
    public void validate() {
        super.validate();
        this.proxy.validate();
    }

    @Override
    public void invalidate() {
        this.unregisterStorageProvider();
        this.shutdownComputationCpus();
        this.proxy.invalidate();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        this.unregisterStorageProvider();
        this.unregisterComputationCpus();
        this.proxy.onChunkUnload();
        super.onChunkUnload();
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        this.refreshSubsystemFromBlock();
        if (!this.networkReady) {
            this.proxy.onReady();
            this.networkReady = true;
        }
        if (this.computationCpuRefreshQueued
            && this.worldObj.getTotalWorldTime() > this.computationCpuRefreshQueuedTick) {
            this.computationCpuRefreshQueued = false;
            this.refreshSubsystemRegistration();
        }
        if (this.worldObj.getTotalWorldTime() % 20L == 0L) {
            this.refreshSubsystemRegistration();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.unregisterComputationCpus();
        this.setSubsystem(ECOControllerSubsystem.fromId(tag.getString(TAG_SUBSYSTEM)));
        this.computationCpuPool.readFromNBT(tag.getCompoundTag(TAG_COMPUTATION_CPU_POOL));
        this.proxy.readFromNBT(tag);
        this.proxy.setVisualRepresentation(this.interfaceStack());
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString(TAG_SUBSYSTEM, this.subsystem.getId());
        if (this.subsystem == ECOControllerSubsystem.COMPUTATION || this.computationCpuPool.hasPersistentState()) {
            NBTTagCompound poolTag = new NBTTagCompound();
            this.computationCpuPool.writeToNBT(poolTag);
            tag.setTag(TAG_COMPUTATION_CPU_POOL, poolTag);
        }
        this.proxy.writeToNBT(tag);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(
            TAG_SUBSYSTEM,
            this.currentBlockSubsystem()
                .getId());
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        this.setSubsystem(ECOControllerSubsystem.fromId(tag.getString(TAG_SUBSYSTEM)));
        if (this.worldObj != null) {
            this.worldObj.markBlockRangeForRenderUpdate(
                this.xCoord,
                this.yCoord,
                this.zCoord,
                this.xCoord,
                this.yCoord,
                this.zCoord);
        }
    }

    private void refreshSubsystemFromBlock() {
        this.setSubsystem(this.currentBlockSubsystem());
    }

    private void refreshSubsystemRegistration() {
        if (this.subsystem == ECOControllerSubsystem.STORAGE) {
            this.refreshBackendRegistration();
        }
        if (this.subsystem == ECOControllerSubsystem.COMPUTATION) {
            this.refreshComputationCpuRegistration();
        }
    }

    private ECOControllerSubsystem currentBlockSubsystem() {
        if (this.worldObj != null
            && this.worldObj.getBlock(this.xCoord, this.yCoord, this.zCoord) instanceof BlockECOInterface) {
            return ((BlockECOInterface) this.worldObj.getBlock(this.xCoord, this.yCoord, this.zCoord)).getSubsystem();
        }
        return this.subsystem;
    }

    private void setSubsystem(ECOControllerSubsystem subsystem) {
        ECOControllerSubsystem normalized = subsystem == null ? ECOControllerSubsystem.STORAGE : subsystem;
        if (this.subsystem == normalized) {
            this.proxy.setVisualRepresentation(this.interfaceStack());
            return;
        }
        this.unregisterStorageProvider();
        this.subsystem = normalized;
        this.proxy.setVisualRepresentation(this.interfaceStack());
        this.cachedController = null;
        this.shutdownComputationCpus();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.markDirty();
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    private void refreshBackendRegistration() {
        if (this.subsystem != ECOControllerSubsystem.STORAGE) {
            this.unregisterStorageProvider();
            return;
        }

        TileECOController previousController = this.cachedController;
        TileECOController controller = this.findController();
        if (controller == null || !controller.isFormed()) {
            this.unregisterStorageProvider();
            this.refreshDriveOnlineStates(previousController);
            return;
        }

        IStorageGrid storageGrid = this.currentStorageGrid();
        if (storageGrid == null) {
            this.unregisterStorageProvider();
            this.refreshDriveOnlineStates(controller);
            return;
        }

        int controllerRevision = controller.getStorageBackendRevision();
        if (this.registeredStorageGrid == storageGrid && this.cachedController == controller
            && this.registeredControllerRevision == controllerRevision
            && this.registeredCellProvider != null) {
            return;
        }

        this.unregisterStorageProvider();
        ICellProvider provider = controller.createStorageDriveProvider();
        storageGrid.registerCellProvider(provider);
        this.registeredStorageGrid = storageGrid;
        this.registeredCellProvider = provider;
        this.cachedController = controller;
        this.registeredControllerRevision = controllerRevision;
        this.postCellArrayUpdate();
        this.refreshDriveOnlineStates(controller);
        NeoECOAE.LOG.debug(
            "Registered ECO storage interface at {},{},{} to controller {},{},{}",
            this.xCoord,
            this.yCoord,
            this.zCoord,
            controller.xCoord,
            controller.yCoord,
            controller.zCoord);
    }

    private void refreshComputationCpuRegistration() {
        if (this.subsystem != ECOControllerSubsystem.COMPUTATION) {
            this.unregisterComputationCpus();
            return;
        }
        TileECOController controller = this.findController();
        if (controller == null || !controller.isFormed()) {
            this.unregisterComputationCpus();
            return;
        }
        ICraftingGrid craftingGrid = this.currentCraftingGrid();
        if (craftingGrid == null) {
            this.unregisterComputationCpus();
            return;
        }
        IGridNode node = this.proxy.getNode();
        this.computationCpuPool.refresh(
            node == null ? null : node.getGrid(),
            craftingGrid,
            controller.getComputationHostStats(),
            node != null && node.isActive(),
            controller.getComputationCpuSelectionMode());
    }

    private void unregisterComputationCpus() {
        this.computationCpuPool.detach();
    }

    private void shutdownComputationCpus() {
        this.computationCpuPool.shutdown();
    }

    private void unregisterStorageProvider() {
        if (this.registeredStorageGrid != null && this.registeredCellProvider != null) {
            this.registeredStorageGrid.unregisterCellProvider(this.registeredCellProvider);
            this.postCellArrayUpdate();
            this.refreshDriveOnlineStates(this.cachedController);
        }
        this.registeredStorageGrid = null;
        this.registeredCellProvider = null;
        this.cachedController = null;
        this.registeredControllerRevision = -1;
    }

    private IStorageGrid currentStorageGrid() {
        try {
            return this.proxy.getStorage();
        } catch (GridAccessException ignored) {
            return null;
        }
    }

    private ICraftingGrid currentCraftingGrid() {
        try {
            return this.proxy.getCrafting();
        } catch (GridAccessException ignored) {
            return null;
        }
    }

    private TileECOController findController() {
        if (this.worldObj == null) {
            return null;
        }
        if (this.cachedController != null && this.cachedController.getWorldObj() == this.worldObj
            && this.cachedController.getSubsystem() == this.subsystem
            && this.cachedController.isFormed()) {
            return this.cachedController;
        }
        TileECOController best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (TileECOController controller : ECOControllerRegistry.controllers(this.worldObj)) {
            if (controller.getSubsystem() != this.subsystem || !controller.isFormed()
                || !controller.isHiddenMember(this.xCoord, this.yCoord, this.zCoord)) {
                continue;
            }
            int distance = Math.abs(this.xCoord - controller.xCoord) + Math.abs(this.yCoord - controller.yCoord)
                + Math.abs(this.zCoord - controller.zCoord);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = controller;
            }
        }
        this.cachedController = best;
        return best;
    }

    private void postCellArrayUpdate() {
        try {
            this.proxy.getGrid()
                .postEvent(new MENetworkCellArrayUpdate());
        } catch (GridAccessException ignored) {}
    }

    private void refreshDriveOnlineStates(TileECOController controller) {
        if (controller == null || this.worldObj == null) {
            return;
        }
        for (ECOFormationBlockPos pos : controller.getFormedMemberBlocks()) {
            TileEntity tile = this.worldObj.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile instanceof TileECODrive) {
                ((TileECODrive) tile).refreshOnlineState(controller);
            }
        }
    }

    private ItemStack interfaceStack() {
        if (this.subsystem == ECOControllerSubsystem.CRAFTING) {
            return new ItemStack(NEBlocks.craftingInterface);
        }
        if (this.subsystem == ECOControllerSubsystem.COMPUTATION) {
            return new ItemStack(NEBlocks.computationInterface);
        }
        return new ItemStack(NEBlocks.storageInterface);
    }
}
