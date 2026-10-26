package cn.dancingsnow.neoecoae.platform.ae2.grid;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.AENetworkProxyMultiblock;
import appeng.tile.grid.AENetworkTile;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.FormationState;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockStateProperties;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeStateBlock;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeStateHolder;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.sync.ManagedFields;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;

/** Forge lifecycle and description packets for the imported entity hierarchy. */
public abstract class NativeNetworkBlockEntity extends AENetworkTile
    implements NativeStateHolder, appeng.api.storage.ICellContainer {

    protected WorldView level;
    protected BlockPos worldPosition;
    private final BlockEntityType<?> type;
    private BlockState state;
    private final ManagedNode mainNode;
    private final java.util.Map<appeng.api.storage.StorageChannel, java.util.List<appeng.api.storage.IMEInventoryHandler>> storageMounts = new java.util.EnumMap<>(
        appeng.api.storage.StorageChannel.class);

    protected NativeNetworkBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        this.type = type;
        this.worldPosition = position;
        this.state = state;
        mainNode = createMainNode();
    }

    @Override
    protected AENetworkProxy createProxy() {
        return this instanceof appeng.me.cluster.IAEMultiBlock
            ? new AENetworkProxyMultiblock(this, "proxy", null, true) {

                @Override
                public java.util.Iterator<IGridNode> getMultiblockNodes() {
                    IGridMultiblock service = mainNode == null ? null : mainNode.service(IGridMultiblock.class);
                    return service == null ? super.getMultiblockNodes() : service.getMultiblockNodes();
                }
            }
            : new AENetworkProxy(this, "proxy", null, true);
    }

    protected ManagedNode createMainNode() {
        return GridHelper.createManagedNode(this, new BlockNodeListener<>());
    }

    public ManagedNode getMainNode() {
        return mainNode;
    }

    public IGridNode getGridNode() {
        return mainNode.getNode();
    }

    public BlockEntityType<?> getType() {
        return type;
    }

    public WorldView getLevel() {
        bindWorld();
        return level;
    }

    public boolean hasLevel() {
        return worldObj != null;
    }

    public BlockPos getBlockPos() {
        bindWorld();
        return worldPosition;
    }

    public BlockState getBlockState() {
        return state;
    }

    public ItemStack getItemFromBlockEntity() {
        return new ItemStack(state.getBlock());
    }

    public boolean isRemoved() {
        return isInvalid();
    }

    public boolean notLoaded() {
        return worldObj == null || !WorldView.of(worldObj)
            .hasChunkAt(getBlockPos());
    }

    private void bindWorld() {
        if (worldObj != null) {
            level = WorldView.of(worldObj);
            worldPosition = new BlockPos(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public BlockState nativeBlockState() {
        return state;
    }

    @Override
    public void nativeBlockState(BlockState state) {
        this.state = state;
        markDirty();
    }

    @Override
    public void validate() {
        bindWorld();
        super.validate();
    }

    @Override
    public void onReady() {
        bindWorld();
        if (state.getBlock() instanceof NativeStateBlock)
            state = mergeFacing(state, ((NativeStateBlock) state.getBlock()).fromMetadata(getBlockMetadata()));
        getProxy().setVisualRepresentation(getItemFromBlockEntity());
        super.onReady();
        mainNode.ready(true);
    }

    private static BlockState mergeFacing(BlockState stored, BlockState metadata) {
        if (stored.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return stored.setValue(
            BlockStateProperties.HORIZONTAL_FACING,
            metadata.getValue(BlockStateProperties.HORIZONTAL_FACING));
        return stored;
    }

    @Override
    public void onChunkUnload() {
        onChunkUnloaded();
    }

    public void onChunkUnloaded() {
        mainNode.ready(false);
        if (this instanceof appeng.me.cluster.IAEMultiBlock) ((appeng.me.cluster.IAEMultiBlock) this).disconnect(false);
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        setRemoved();
    }

    public void setRemoved() {
        mainNode.ready(false);
        if (this instanceof appeng.me.cluster.IAEMultiBlock) ((appeng.me.cluster.IAEMultiBlock) this).disconnect(false);
        super.invalidate();
    }

    @Override
    public void gridChanged() {
        if (mainNode != null) mainNode.gridChanged();
    }

    public void onMainNodeStateChanged(NodeListener.State reason) {
        markForClientUpdate();
    }

    @MENetworkEventSubscribe
    public void nativePowerChanged(MENetworkPowerStatusChange event) {
        mainNode.stateChanged(NodeListener.State.POWER);
        refreshNativeStorage();
    }

    @MENetworkEventSubscribe
    public void nativeChannelsChanged(MENetworkChannelsChanged event) {
        mainNode.stateChanged(NodeListener.State.CHANNEL);
        refreshNativeStorage();
    }

    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    public void onGridConnectableSidesChanged() {
        mainNode.setExposedOnSides(
            getGridConnectableSides(
                new BlockOrientation(
                    state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                        .orElse(Direction.NORTH),
                    Direction.UP)));
    }

    @Override
    public ForgeDirection getForward() {
        return state == null ? ForgeDirection.NORTH
            : state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                .orElse(Direction.NORTH)
                .nativeSide();
    }

    @Override
    public ForgeDirection getUp() {
        return ForgeDirection.UP;
    }

    @Override
    public void setOrientation(ForgeDirection front, ForgeDirection top) {
        if (front == ForgeDirection.UNKNOWN || front == ForgeDirection.UP
            || front == ForgeDirection.DOWN
            || top != ForgeDirection.UP) throw new IllegalArgumentException("ECO host orientation must be horizontal");
        super.setOrientation(front, top);
        if (state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.fromNative(front));
            if (worldObj != null) WorldView.of(worldObj)
                .setBlock(getBlockPos(), state, 2);
        }
        if (mainNode != null && mainNode.isReady()
            && this instanceof cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAEMultiBlock
            && worldObj != null
            && !worldObj.isRemote)
            ((cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAEMultiBlock<?>) this).rebuildMultiblock();
    }

    public void setChanged() {
        markDirty();
    }

    public void saveChanges() {
        setChanged();
    }

    public void scheduleRenderUpdate() {
        markForClientUpdate();
    }

    public void addAdditionalDrops(WorldView world, BlockPos pos, java.util.List<ItemStack> drops) {}

    @Override
    public void saveChanges(appeng.api.storage.IMEInventory inventory) {
        saveChanges();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public java.util.List<appeng.api.storage.IMEInventoryHandler> getCellArray(
        appeng.api.storage.StorageChannel channel) {
        if (!mainNode.isOnline()) return java.util.Collections.emptyList();
        return storageMounts.computeIfAbsent(channel, key -> {
            var provider = mainNode.service(cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageProvider.class);
            java.util.List<appeng.api.storage.IMEInventoryHandler> views = new java.util.ArrayList<>();
            if (provider != null) provider.mountInventories(
                (inventory, priority) -> views.add(
                    new cn.dancingsnow.neoecoae.platform.ae2.storage.NativeStorageView<>(inventory, key, priority)));
            return java.util.Collections.unmodifiableList(views);
        });
    }

    public void refreshNativeStorage() {
        storageMounts.clear();
        if (mainNode.isReady() && mainNode.getNode() != null) mainNode.getNode()
            .getGrid()
            .postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
    }

    public void markForClientUpdate() {
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.WORLD_NBT_READ)
    public void readNativeFields(NBTTagCompound tag) {
        readState(tag.getCompoundTag("neoecoae_state"));
        loadTag(new CompoundTag(tag), RegistryAccess.EMPTY);
    }

    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.WORLD_NBT_WRITE)
    public void writeNativeFields(NBTTagCompound tag) {
        tag.setTag("neoecoae_state", writeState());
        saveAdditional(new CompoundTag(tag), RegistryAccess.EMPTY);
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        ManagedFields.read(this, tag.getCompound("neoecoae_fields"), false);
    }

    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("neoecoae_fields", ManagedFields.write(this, false));
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.nativeTag()
            .setTag("neoecoae_state", writeState());
        return tag;
    }

    private NBTTagCompound writeState() {
        NBTTagCompound tag = new NBTTagCompound();
        state.getValues()
            .forEach((property, value) -> tag.setString(property.getName(), value.toString()));
        return tag;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void readState(NBTTagCompound tag) {
        for (Property property : state.getProperties()) {
            if (!tag.hasKey(property.getName(), 8)) continue;
            String value = tag.getString(property.getName());
            Object decoded = property.type() == Boolean.class ? Boolean.valueOf(value)
                : property.type() == Integer.class ? Integer.valueOf(value) : Enum.valueOf(property.type(), value);
            state = state.setValue(property, decoded);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, getUpdateTag(RegistryAccess.EMPTY).nativeTag());
    }

    @Override
    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        readState(tag.getCompoundTag("neoecoae_state"));
        if (this instanceof ManagedHolder)
            ManagedFields.read(this, new CompoundTag(tag).getCompound(((ManagedHolder) this).getSyncTag()), true);
        if (this instanceof FormationState) for (Property<?> property : state.getProperties()) if (property.getName()
            .equals("formed"))
            ((FormationState) this).setFormed(
                (Boolean) state.getValues()
                    .get(property));
        markForClientUpdate();
    }

    public void clearContent() {}

    public void saveToItem(net.minecraft.item.ItemStack stack, HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        saveAdditional(tag, registries);
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setTag("BlockEntityTag", tag.nativeTag());
    }
}
