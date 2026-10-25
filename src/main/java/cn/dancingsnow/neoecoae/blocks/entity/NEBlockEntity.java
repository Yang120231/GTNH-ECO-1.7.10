package cn.dancingsnow.neoecoae.blocks.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.api.power.ECOPowerProfiles;
import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.BlockNodeListener;
import cn.dancingsnow.neoecoae.platform.ae2.grid.BlockOrientation;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridHelper;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IGridMultiblock;
import cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NodeListener;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAEMultiBlock;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.sync.ManagedHolder;

public abstract class NEBlockEntity<C extends NECluster<C>, E extends NEBlockEntity<C, E>>
    extends NativeNetworkBlockEntity implements IAEMultiBlock<C> {

    public boolean isFormed() {
        return formed;
    }

    public void setFormed(boolean value) {
        formed = value;
    }

    public C getCluster() {
        return cluster;
    }

    public NEClusterCalculator<C> getCalculator() {
        return calculator;
    }

    private static final NodeListener<NEBlockEntity<?, ?>> NODE_LISTENER = new BlockNodeListener<>() {

        @Override
        public void onGridChanged(NEBlockEntity<?, ?> nodeOwner, IGridNode node) {
            nodeOwner.onMainNodeGridChanged();
        }
    };

    protected boolean formed = false;

    @Nullable
    protected C cluster;

    protected final NEClusterCalculator<C> calculator;

    /**
     * Calculators chosen per block entity type instead of per block entity class.
     *
     * <p>
     * An addon that registers its own {@link BlockEntityType} still has to use this mod's block entity
     * classes, because AE2 files grid nodes under {@code owner.getClass()} and looks machines up by that
     * exact key - so the only place left where an addon can reach the multiblock logic is the type, which
     * it does own. Without this the addon has to replace the whole {@code verifyInternalStructure}, and
     * every side effect that method grows later (mirrored flags, network switch state) is silently lost.
     */
    private static final Map<BlockEntityType<?>, NEClusterCalculator.Factory<?>> CALCULATOR_FACTORIES = new ConcurrentHashMap<>();

    /**
     * Uses {@code factory} instead of this mod's own calculator for one block entity type. The factory is
     * called with the block entity the same way the built-in one is, so implementations are free to
     * subclass {@code NEComputationClusterCalculator} / {@code NECraftingClusterCalculator} and override
     * only {@code verifyStructure}.
     */
    public static <C extends NECluster<C>> void registerCalculatorFactory(BlockEntityType<?> type,
        NEClusterCalculator.Factory<C> factory) {
        CALCULATOR_FACTORIES.put(type, factory);
    }

    @SuppressWarnings("unchecked")
    private static <C extends NECluster<C>> NEClusterCalculator<C> createCalculator(BlockEntityType<?> type,
        NEBlockEntity<C, ?> blockEntity, NEClusterCalculator.Factory<C> fallback) {
        NEClusterCalculator.Factory<?> registered = CALCULATOR_FACTORIES.get(type);
        NEClusterCalculator.Factory<C> factory = registered == null ? fallback
            : (NEClusterCalculator.Factory<C>) registered;
        return factory.create(blockEntity);
    }

    public NEBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
        NEClusterCalculator.Factory<C> calculator) {
        super(type, pos, blockState);
        this.calculator = createCalculator(type, this, calculator);
        getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);
        onGridConnectableSidesChanged();
    }

    @Override
    public void onReady() {
        super.onReady();
        onGridConnectableSidesChanged();
        if (level instanceof ServerWorldView serverLevel) {
            calculator.calculateMultiblock(serverLevel, worldPosition);
            // During chunk loading, neighboring block entities may not be available yet. A single
            // immediate calculation can therefore leave a valid structure disconnected until a
            // block update occurs. Retry after the load queue has finished restoring the chunk.
            serverLevel.getServer()
                .executeIfPossible(() -> {
                    if (!isRemoved() && level == serverLevel && hasLevel() && level.hasChunkAt(worldPosition)) {
                        calculator.calculateMultiblock(serverLevel, worldPosition);
                    }
                });
        }
        applyIdlePower(ECOPowerRole.COMPONENT, 16, 0);
    }

    private ECOPowerRole idlePowerRole = ECOPowerRole.COMPONENT;
    private double idleBasePower = 16;
    private double idleCellDrain;
    private double appliedIdlePower = Double.NaN;

    /** Tier available to power providers; untiered parts may return null. */
    @Nullable
    public cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor getTier() {
        return null;
    }

    public final void applyIdlePower(ECOPowerRole role, double base, double cellDrain) {
        idlePowerRole = role;
        idleBasePower = base;
        idleCellDrain = cellDrain;
        var tier = getTier();
        double defaultBase = role == ECOPowerRole.COMPONENT && tier != null ? tier.getComponentIdlePower() : base;
        double resolved = ECOPowerProfiles
            .resolve(new ECOPowerProfiles.Context(this, getBlockState(), tier, role, defaultBase, cellDrain));
        if (Double.compare(appliedIdlePower, resolved) != 0) {
            appliedIdlePower = resolved;
            getMainNode().setIdlePowerUsage(resolved);
        }
    }

    /** Call after changing an addon power profile or addon-owned block state. */
    public final void refreshIdlePower() {
        applyIdlePower(idlePowerRole, idleBasePower, idleCellDrain);
    }

    /** The exact value last applied to the AE2 node; empty before the first power update. */
    public final java.util.OptionalDouble appliedIdlePower() {
        return Double.isNaN(appliedIdlePower) ? java.util.OptionalDouble.empty()
            : java.util.OptionalDouble.of(appliedIdlePower);
    }

    public void updateMultiBlock(BlockPos changedPos) {
        if (isServerStopping()) {
            return;
        }
        if (level instanceof ServerWorldView serverLevel) {
            calculator.updateMultiblockAfterNeighborUpdate(serverLevel, worldPosition, changedPos);
        }
    }

    public void rebuildMultiblock() {
        if (isServerStopping()) {
            return;
        }
        if (level instanceof ServerWorldView serverLevel) {
            calculator.calculateMultiblock(serverLevel, worldPosition);
        }
    }

    @Override
    protected ManagedNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    @Override
    public void onMainNodeStateChanged(NodeListener.State reason) {
        if (isServerStopping()) {
            return;
        }
        if (reason != NodeListener.State.GRID_BOOT) {
            this.updateState(false);
        }
    }

    protected void onMainNodeGridChanged() {}

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed) {
            return EnumSet.noneOf(Direction.class);
        }
        if (isServerStopping()) {
            return EnumSet.noneOf(Direction.class);
        }

        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        if (level != null) {
            for (Direction value : Direction.values()) {
                BlockPos adjacentPos = this.worldPosition.relative(value);
                if (level.hasChunkAt(adjacentPos) && level.getBlockEntity(adjacentPos) instanceof IGridHost) {
                    directions.add(value);
                }
            }
        }
        return directions;
    }

    @MustBeInvokedByOverriders
    public void updateState(boolean updateExposed) {
        if (this.level == null || this.notLoaded() || this.isRemoved() || isServerStopping()) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        BlockState newState = state;
        if (state.hasProperty(NativeMachineBlock.FORMED)) {
            newState = state.setValue(NativeMachineBlock.FORMED, formed);
        } else if (state.hasProperty(NativeMachineBlock.FORMED)) {
            newState = state.setValue(NativeMachineBlock.FORMED, formed);
        }
        if (newState != state) {
            level.setBlock(worldPosition, newState, 2);
        }
        refreshIdlePower();
        if (updateExposed && getMainNode().isReady()) {
            onGridConnectableSidesChanged();
        }
    }

    /**
     * AEBaseBlockEntity overrides getUpdateTag without calling TileEntity#getUpdateTag, so LDLib's
     * TileEntity mixin cannot append the initial values of our managed sync fields. Keep the bridge
     * here so clients receive cell, multiblock and orientation data before section geometry is built.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (this instanceof ManagedHolder syncManagedHolder) {
            tag.put(syncManagedHolder.getSyncTag(), syncManagedHolder.serializeInitialData(registries));
        }
        return tag;
    }

    private Iterator<IGridNode> getMultiblockNodes() {
        if (cluster == null) {
            return java.util.Collections.emptyIterator();
        }
        List<IGridNode> nodes = new ArrayList<>();
        Iterator<? extends net.minecraft.tileentity.TileEntity> it = cluster.getBlockEntities();
        while (it.hasNext()) {
            net.minecraft.tileentity.TileEntity blockEntity = it.next();
            IGridNode node = blockEntity instanceof NEBlockEntity<?, ?>member ? member.getGridNode() : null;
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes.listIterator();

    }

    public void updateCluster(@Nullable C cluster) {
        this.cluster = cluster;
        formed = cluster != null;
        updateState(true);
    }

    protected boolean isServerStopping() {
        return this.level instanceof ServerWorldView serverLevel && serverLevel.getServer()
            .isStopped();
    }

    @Override
    public void disconnect(boolean update) {
        if (this.cluster != null) {
            this.cluster.destroy();
            formed = false;
            if (update) {
                updateState(true);
            }
        }
    }

    @Override
    public boolean isValid() {
        return !this.isRemoved();
    }

    public void breakCluster() {
        if (this.cluster != null) {
            cluster.breakCluster();
        }
    }
}
