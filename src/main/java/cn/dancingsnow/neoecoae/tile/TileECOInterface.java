package cn.dancingsnow.neoecoae.tile;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.cache.CraftingGridCache;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.block.BlockECOInterface;
import cn.dancingsnow.neoecoae.computation.ComputationTaskInfo;
import cn.dancingsnow.neoecoae.computation.ae2.ECOComputationCpuPool;
import cn.dancingsnow.neoecoae.crafting.ae2.ECOCraftingAe2Registration;
import cn.dancingsnow.neoecoae.energy.ECOEnergyProfile;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.storage.ae2.ECOStorageDriveProvider;
import cn.dancingsnow.neoecoae.storage.domain.ECOStorageInterfaceMode;

public class TileECOInterface extends TileEntity
    implements IGridProxyable, IActionHost, IPriorityHost, ICraftingProvider {

    private static final String TAG_SUBSYSTEM = "Subsystem";
    private static final String TAG_COMPUTATION_CPU_POOL = "ComputationCpuPool";
    private static final String TAG_STORAGE_INTERFACE_MODE = "StorageInterfaceMode";
    private static final String TAG_STORAGE_INTERFACE_TRANSFERRED_LAST_TICK = "StorageInterfaceTransferredLastTick";
    private static final String TAG_STORAGE_INTERFACE_TRANSFERRED_TOTAL = "StorageInterfaceTransferredTotal";
    private ECOControllerSubsystem subsystem = ECOControllerSubsystem.STORAGE;
    private final AENetworkProxy proxy;
    private IStorageGrid registeredStorageGrid;
    private ICellProvider registeredCellProvider;
    private ECOStorageDriveProvider transferProvider;
    private int transferProviderRevision = -1;
    private TileECOController cachedController;
    private int registeredControllerRevision = -1;
    private boolean networkReady;
    private boolean computationCpuRefreshQueued;
    private long computationCpuRefreshQueuedTick;
    private final ECOComputationCpuPool computationCpuPool = new ECOComputationCpuPool(this);
    private boolean craftingProviderRefreshQueued;
    private long craftingProviderRefreshQueuedTick;
    private final ECOCraftingAe2Registration craftingAe2Registration = new ECOCraftingAe2Registration(this);
    private ECOStorageInterfaceMode storageInterfaceMode = ECOStorageInterfaceMode.STORAGE;
    private long storageInterfaceTransferredLastTick;
    private long storageInterfaceTransferredTotal;

    public TileECOInterface() {
        this(ECOControllerSubsystem.STORAGE);
    }

    public TileECOInterface(ECOControllerSubsystem subsystem) {
        this.subsystem = subsystem == null ? ECOControllerSubsystem.STORAGE : subsystem;
        this.proxy = new AENetworkProxy(this, "proxy", this.interfaceStack(), true);
        this.proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        this.proxy.setIdlePowerUsage(ECOEnergyProfile.INTERFACE_IDLE_POWER);
        this.proxy.setValidSides(EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN)));
    }

    public ECOControllerSubsystem getSubsystem() {
        return this.subsystem;
    }

    public ECOStorageInterfaceMode getStorageInterfaceMode() {
        return this.storageInterfaceMode;
    }

    public boolean isStorageInputMode() {
        return this.storageInterfaceMode == ECOStorageInterfaceMode.INPUT;
    }

    public boolean isStorageOutputMode() {
        return this.storageInterfaceMode == ECOStorageInterfaceMode.OUTPUT;
    }

    public boolean isStorageTransferMode() {
        return this.subsystem == ECOControllerSubsystem.STORAGE && this.storageInterfaceMode.isTransfer();
    }

    public long getStorageInterfaceTransferredLastTick() {
        return this.storageInterfaceTransferredLastTick;
    }

    public long getStorageInterfaceTransferredTotal() {
        return this.storageInterfaceTransferredTotal;
    }

    public void setStorageInterfaceMode(ECOStorageInterfaceMode mode) {
        ECOStorageInterfaceMode normalized = mode == null ? ECOStorageInterfaceMode.STORAGE : mode;
        if (this.storageInterfaceMode == normalized) {
            return;
        }
        this.storageInterfaceMode = normalized;
        this.storageInterfaceTransferredLastTick = 0L;
        this.markDirty();
        if (this.worldObj != null && !this.worldObj.isRemote) {
            TileECOController controller = this.getBoundController();
            if (controller != null) {
                controller.onStorageInterfaceModeChanged();
            }
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    public void cycleStorageInterfaceMode() {
        this.setStorageInterfaceMode(this.storageInterfaceMode.next());
    }

    public void recordStorageInterfaceTransfer(long amount) {
        long safeAmount = Math.max(0L, amount);
        this.storageInterfaceTransferredLastTick = safeAmount;
        if (safeAmount > 0L) {
            if (Long.MAX_VALUE - this.storageInterfaceTransferredTotal < safeAmount) {
                this.storageInterfaceTransferredTotal = Long.MAX_VALUE;
            } else {
                this.storageInterfaceTransferredTotal += safeAmount;
            }
            this.markDirty();
        }
    }

    public IStorageGrid getStorageGridForTransfer() {
        return this.subsystem == ECOControllerSubsystem.STORAGE ? this.currentStorageGrid() : null;
    }

    public List<IMEInventoryHandler> getStorageTransferHandlers(TileECOController controller, StorageChannel channel) {
        if (controller == null || channel == null || this.subsystem != ECOControllerSubsystem.STORAGE) {
            return Collections.emptyList();
        }
        int revision = controller.getStorageBackendRevision();
        if (this.transferProvider == null || this.transferProviderRevision != revision) {
            this.transferProvider = controller.createStorageDriveProvider();
            this.transferProviderRevision = revision;
        }
        return this.transferProvider.getCellArray(channel);
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

    public void requestCraftingProviderRefresh() {
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        this.craftingProviderRefreshQueued = true;
        this.craftingProviderRefreshQueuedTick = this.worldObj.getTotalWorldTime();
    }

    public ItemStack injectCraftingOutput(ItemStack stack) {
        return this.injectCraftingOutput(stack, false);
    }

    public ItemStack injectCraftingRecovery(ItemStack stack) {
        return this.injectCraftingStack(stack, false, false);
    }

    public long injectCraftingOutput(ItemStack prototype, long amount) {
        return this.injectCraftingAmount(prototype, amount, false, true);
    }

    public long injectCraftingRecovery(ItemStack prototype, long amount) {
        return this.injectCraftingAmount(prototype, amount, false, false);
    }

    public IAEItemStack extractCraftingInput(IAEItemStack stack, boolean simulate) {
        if (stack == null || stack.getStackSize() <= 0L) {
            return null;
        }
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return null;
        }
        IStorageGrid storageGrid = this.currentStorageGrid();
        if (storageGrid == null) {
            return null;
        }
        return storageGrid.getItemInventory()
            .extractItems(stack.copy(), simulate ? Actionable.SIMULATE : Actionable.MODULATE, new MachineSource(this));
    }

    public ItemStack injectCraftingOutput(ItemStack stack, boolean simulate) {
        return this.injectCraftingStack(stack, simulate, true);
    }

    private ItemStack injectCraftingStack(ItemStack stack, boolean simulate, boolean offerToCraftingCpus) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        long remaining = this.injectCraftingAmount(stack, stack.stackSize, simulate, offerToCraftingCpus);
        return copyCraftingRemainder(stack, remaining);
    }

    private long injectCraftingAmount(ItemStack prototype, long amount, boolean simulate, boolean offerToCraftingCpus) {
        if (prototype == null || amount <= 0L) {
            return 0L;
        }
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return amount;
        }
        IAEItemStack aeStack = createCraftingAmount(prototype, amount);
        if (aeStack == null) {
            return amount;
        }
        Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        MachineSource source = new MachineSource(this);
        IAEItemStack leftover = aeStack;
        if (offerToCraftingCpus) {
            ICraftingGrid craftingGrid = this.currentCraftingGrid();
            if (craftingGrid instanceof CraftingGridCache) {
                leftover = (IAEItemStack) ((CraftingGridCache) craftingGrid).injectItems(leftover, mode, source);
            }
        }
        IStorageGrid storageGrid = this.currentStorageGrid();
        if (leftover != null && storageGrid != null) {
            leftover = storageGrid.getItemInventory()
                .injectItems(leftover, mode, source);
        }
        return leftover == null ? 0L : Math.max(0L, Math.min(amount, leftover.getStackSize()));
    }

    private static IAEItemStack createCraftingAmount(ItemStack prototype, long amount) {
        if (prototype == null || amount <= 0L) {
            return null;
        }
        IAEItemStack stack = AEItemStack.create(prototype);
        return stack == null ? null : stack.setStackSize(amount);
    }

    public FluidStack extractCraftingFluid(FluidStack stack, boolean simulate) {
        if (stack == null || stack.amount <= 0) {
            return null;
        }
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            return null;
        }
        IStorageGrid storageGrid = this.currentStorageGrid();
        if (storageGrid == null) {
            return null;
        }
        IAEFluidStack aeStack = AEApi.instance()
            .storage()
            .createFluidStack(stack.copy());
        if (aeStack == null) {
            return null;
        }
        IMEMonitor<IAEFluidStack> fluids = storageGrid.getFluidInventory();
        if (fluids == null) {
            return null;
        }
        IAEFluidStack extracted = fluids
            .extractItems(aeStack, simulate ? Actionable.SIMULATE : Actionable.MODULATE, new MachineSource(this));
        if (extracted == null || extracted.getStackSize() <= 0L) {
            return null;
        }
        FluidStack extractedStack = extracted.getFluidStack();
        return extractedStack == null ? null : extractedStack.copy();
    }

    private static ItemStack copyCraftingRemainder(ItemStack original, long remainingAmount) {
        if (original == null || original.stackSize <= 0 || remainingAmount <= 0L) {
            return null;
        }
        long boundedRemaining = Math.min((long) original.stackSize, remainingAmount);
        if (boundedRemaining <= 0L) {
            return null;
        }
        ItemStack remaining = original.copy();
        remaining.stackSize = (int) boundedRemaining;
        return remaining;
    }

    public double extractAEPower(double amount, boolean simulate) {
        if (amount <= 0D) {
            return 0D;
        }
        try {
            return this.proxy.getEnergy()
                .extractAEPower(amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, PowerMultiplier.CONFIG);
        } catch (GridAccessException ignored) {
            return 0D;
        }
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
        this.refreshSubsystemRegistration(false);
    }

    @Override
    public void validate() {
        super.validate();
        this.proxy.validate();
    }

    @Override
    public void invalidate() {
        this.unregisterStorageProvider();
        this.unregisterCraftingProvider();
        this.shutdownComputationCpus();
        this.transferProvider = null;
        this.transferProviderRevision = -1;
        this.proxy.invalidate();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        this.unregisterStorageProvider();
        this.unregisterCraftingProvider();
        this.unregisterComputationCpus();
        this.transferProvider = null;
        this.transferProviderRevision = -1;
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
            this.refreshSubsystemRegistration(false);
        }
        if (this.craftingProviderRefreshQueued
            && this.worldObj.getTotalWorldTime() > this.craftingProviderRefreshQueuedTick) {
            this.craftingProviderRefreshQueued = false;
            this.refreshSubsystemRegistration(true);
        }
        if (this.worldObj.getTotalWorldTime() % 20L == 0L) {
            this.refreshSubsystemRegistration(false);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.unregisterComputationCpus();
        this.unregisterCraftingProvider();
        this.setSubsystem(ECOControllerSubsystem.fromId(tag.getString(TAG_SUBSYSTEM)));
        this.storageInterfaceMode = ECOStorageInterfaceMode.byName(tag.getString(TAG_STORAGE_INTERFACE_MODE));
        this.storageInterfaceTransferredLastTick = Math
            .max(0L, tag.getLong(TAG_STORAGE_INTERFACE_TRANSFERRED_LAST_TICK));
        this.storageInterfaceTransferredTotal = Math.max(0L, tag.getLong(TAG_STORAGE_INTERFACE_TRANSFERRED_TOTAL));
        this.computationCpuPool.readFromNBT(tag.getCompoundTag(TAG_COMPUTATION_CPU_POOL));
        this.proxy.readFromNBT(tag);
        this.proxy.setVisualRepresentation(this.interfaceStack());
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString(TAG_SUBSYSTEM, this.subsystem.getId());
        tag.setString(TAG_STORAGE_INTERFACE_MODE, this.storageInterfaceMode.name());
        tag.setLong(TAG_STORAGE_INTERFACE_TRANSFERRED_LAST_TICK, this.storageInterfaceTransferredLastTick);
        tag.setLong(TAG_STORAGE_INTERFACE_TRANSFERRED_TOTAL, this.storageInterfaceTransferredTotal);
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
        tag.setString(TAG_STORAGE_INTERFACE_MODE, this.storageInterfaceMode.name());
        tag.setLong(TAG_STORAGE_INTERFACE_TRANSFERRED_LAST_TICK, this.storageInterfaceTransferredLastTick);
        tag.setLong(TAG_STORAGE_INTERFACE_TRANSFERRED_TOTAL, this.storageInterfaceTransferredTotal);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        this.setSubsystem(ECOControllerSubsystem.fromId(tag.getString(TAG_SUBSYSTEM)));
        this.storageInterfaceMode = ECOStorageInterfaceMode.byName(tag.getString(TAG_STORAGE_INTERFACE_MODE));
        this.storageInterfaceTransferredLastTick = Math
            .max(0L, tag.getLong(TAG_STORAGE_INTERFACE_TRANSFERRED_LAST_TICK));
        this.storageInterfaceTransferredTotal = Math.max(0L, tag.getLong(TAG_STORAGE_INTERFACE_TRANSFERRED_TOTAL));
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
        this.refreshSubsystemRegistration(false);
    }

    private void refreshSubsystemRegistration(boolean forceCraftingPatternRefresh) {
        if (this.subsystem == ECOControllerSubsystem.STORAGE) {
            this.refreshBackendRegistration();
        }
        if (this.subsystem == ECOControllerSubsystem.CRAFTING) {
            this.refreshCraftingProviderRegistration(forceCraftingPatternRefresh);
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
        this.unregisterCraftingProvider();
        this.subsystem = normalized;
        this.proxy.setVisualRepresentation(this.interfaceStack());
        this.cachedController = null;
        this.transferProvider = null;
        this.transferProviderRevision = -1;
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

    private void refreshCraftingProviderRegistration(boolean forcePatternRefresh) {
        if (this.subsystem != ECOControllerSubsystem.CRAFTING) {
            this.unregisterCraftingProvider();
            return;
        }
        TileECOController controller = this.findController();
        if (controller == null || !controller.isFormed()) {
            this.unregisterCraftingProvider();
            return;
        }
        ICraftingGrid craftingGrid = this.currentCraftingGrid();
        if (craftingGrid == null) {
            this.unregisterCraftingProvider();
            return;
        }
        IGridNode node = this.proxy.getNode();
        this.craftingAe2Registration
            .refresh(craftingGrid, node, controller, node != null && node.isActive(), forcePatternRefresh);
    }

    private void unregisterComputationCpus() {
        this.computationCpuPool.detach();
    }

    private void unregisterCraftingProvider() {
        this.craftingAe2Registration.detach();
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

    @Override
    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        this.craftingAe2Registration.provideFallbackCrafting(craftingTracker);
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        return this.craftingAe2Registration.pushFallbackPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        return this.craftingAe2Registration.isFallbackBusy();
    }

    @Override
    public ItemStack getCrafterIcon() {
        return this.craftingAe2Registration.getCrafterIcon();
    }
}
