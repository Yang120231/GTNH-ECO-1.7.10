package cn.dancingsnow.neoecoae.multiblock.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAECluster;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;

public abstract class NECluster<T extends NECluster<T>> implements IAECluster {

    private BlockPos boundMin;
    private BlockPos boundMax;
    protected final List<TileEntity> blockEntities = new ArrayList<>();

    private boolean destroyed = false;
    private ECOMachineInterfaceBlockEntity<?> gridInterface;
    private boolean dirtyGridFaces;

    public final boolean usesCompactGrid() {
        return gridInterface != null;
    }

    public final @Nullable ECOMachineInterfaceBlockEntity<?> getGridInterface() {
        return gridInterface;
    }

    public final void configureCompactGrid() {
        gridInterface = findGridInterface(blockEntities);
    }

    protected final @Nullable ECOMachineInterfaceBlockEntity<?> findGridInterface(List<? extends TileEntity> members) {
        ECOMachineInterfaceBlockEntity<?> result = null;
        for (TileEntity tile : members) {
            if (!(tile instanceof NEBlockEntity<?, ?>member) || !member.defersGridNode()) return null;
            if (tile instanceof ECOMachineInterfaceBlockEntity<?>port) {
                if (result != null || !supportsCompactInterface(port)) return null;
                result = port;
            }
        }
        return result;
    }

    protected boolean supportsCompactInterface(ECOMachineInterfaceBlockEntity<?> port) {
        return true;
    }

    protected final void replaceGridInterface(ECOMachineInterfaceBlockEntity<?> port) {
        if (gridInterface == port) return;
        gridInterface.setDeferredGridNode(false);
        gridInterface = port;
    }

    public final Iterator<IGridNode> gridNodes() {
        if (usesCompactGrid()) {
            IGridNode node = gridInterface.getGridNode();
            return node == null ? Collections.emptyIterator()
                : Collections.singleton(node)
                    .iterator();
        }
        List<IGridNode> nodes = new ArrayList<>();
        for (TileEntity tile : blockEntities) if (tile instanceof NEBlockEntity<?, ?>member) {
            IGridNode node = member.getGridNode();
            if (node != null) nodes.add(node);
        }
        return nodes.iterator();
    }

    public final void memberChanged(NEBlockEntity<?, ?> member) {
        if (usesCompactGrid() && (member == gridInterface || !gridInterface.getProxy()
            .isReady())) {
            dirtyGridFaces = true;
            requestGridPowerUpdate();
        }
    }

    public final void requestGridPowerUpdate() {
        if (!destroyed && usesCompactGrid() && gridInterface.getLevel() instanceof ServerWorldView level)
            NELogicalNetworkManager.requestCompactGridRefresh(level, this);
    }

    public final void refreshGrid(boolean force) {
        if (destroyed || !usesCompactGrid() || !(gridInterface.getLevel() instanceof ServerWorldView level)) return;
        boolean opening = !gridInterface.getProxy()
            .isReady();
        if (opening) for (TileEntity tile : blockEntities) {
            if (!(tile instanceof NEBlockEntity<?, ?>member) || member.isRemoved()
                || member.notLoaded()
                || !member.getMainNode()
                    .isReady())
                return;
        }
        var profiler = level.getServer()
            .nativeServer().theProfiler;
        profiler.startSection("ecoCompactTopology");
        MBCalculator.pauseGridRebuilds();
        try {
            double total = 0;
            for (TileEntity tile : blockEntities) {
                if (tile instanceof NEBlockEntity<?, ?>member) total += member.appliedIdlePower()
                    .orElse(0);
            }
            gridInterface.getMainNode()
                .setIdlePowerUsage(total);
            if (!gridInterface.setDeferredGridNode(true)) return;
            if (opening && !gridInterface.getCalculator()
                .restrictsLegacyGridSides()) warnAboutOldWiring(level);
            if (force || opening || dirtyGridFaces) gridInterface.getMainNode()
                .updateExposedOnSides(gridInterface.getGridConnectableSides(), true);
            dirtyGridFaces = false;
            if (opening) compactGridStateChanged(true);
        } finally {
            MBCalculator.resumeGridRebuilds();
            profiler.endSection();
        }
    }

    private void warnAboutOldWiring(ServerWorldView level) {
        for (TileEntity tile : blockEntities) {
            if (tile == gridInterface || !(tile instanceof NEBlockEntity<?, ?>member)) continue;
            for (Direction side : Direction.values()) {
                var adjacent = member.getBlockPos()
                    .relative(side);
                if (!containsPosition(adjacent) && level.getBlockEntity(adjacent) instanceof IGridHost) {
                    NeoECOAE.LOG.warn(
                        "Compact ECO host at {} (dim {}): ME device next to non-interface part at {}; move the ME connection to the communication interface",
                        gridInterface.getBlockPos(),
                        level.nativeWorld().provider.dimensionId,
                        member.getBlockPos());
                    return;
                }
            }
        }
    }

    public void compactGridStateChanged(boolean gridChanged) {}

    public boolean deferMemberRemoval(NEBlockEntity<?, ?> member) {
        return false;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public NECluster(BlockPos boundMin, BlockPos boundMax) {
        setBounds(boundMin, boundMax);
    }

    protected final void setBounds(BlockPos boundMin, BlockPos boundMax) {
        this.boundMin = boundMin;
        this.boundMax = boundMax;
    }

    @Override
    public BlockPos getBoundsMin() {
        return boundMin;
    }

    @Override
    public BlockPos getBoundsMax() {
        return boundMax;
    }

    public void updateFormed(boolean formed) {
        for (TileEntity blockEntity : this.blockEntities) {
            if (blockEntity instanceof NEBlockEntity<?, ?>be) {
                be.setFormed(formed);
            }
        }
    }

    public boolean shouldCasingHide(NEBlockEntity<T, ?> blockEntity) {
        if (!(blockEntity instanceof ECOMachineCasingBlockEntity)) {
            return false;
        }
        if (hideAllCasingsWhenFormed()) {
            return true;
        }
        BlockPos origin = getCasingHideOrigin();
        return origin != null && blockEntity.getBlockPos()
            .distSqr(origin) <= 3;
    }

    protected boolean hideAllCasingsWhenFormed() {
        return false;
    }

    protected @Nullable BlockPos getCasingHideOrigin() {
        return null;
    }

    public boolean isNetworkMode() {
        return false;
    }

    public int getNetworkMultiplier() {
        return 1;
    }

    public boolean shouldCasingRenderInClassic(NEBlockEntity<T, ?> blockEntity) {
        return false;
    }

    public void addBlockEntity(NEBlockEntity<T, ?> blockEntity) {
        blockEntity.saveChanges();
        this.blockEntities.add(blockEntity);
    }

    public void addBlockEntity(TileEntity blockEntity) {
        this.blockEntities.add(blockEntity);
    }

    @Override
    @MustBeInvokedByOverriders
    public Iterator<? extends TileEntity> getBlockEntities() {
        return blockEntities.listIterator();
    }

    public boolean containsBlockEntity(@Nullable TileEntity blockEntity) {
        return blockEntity != null && blockEntities.contains(blockEntity);
    }

    @Override
    @MustBeInvokedByOverriders
    public void updateStatus(boolean updateGrid) {
        for (TileEntity blockEntity : blockEntities) {
            if (blockEntity instanceof NEBlockEntity<?, ?>be) {
                be.updateState(updateGrid);
            }
        }
    }

    /** Physical block removal. Recalculation and unload must continue to use destroy(). */
    public void breakCluster() {
        destroy();
    }

    @Override
    @MustBeInvokedByOverriders
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        boolean ownsModification = !MBCalculator.isModificationInProgress();
        if (ownsModification) {
            MBCalculator.setModificationInProgress(this);
        }
        MBCalculator.pauseGridRebuilds();
        try {
            if (gridInterface != null) gridInterface.setDeferredGridNode(false);
            for (TileEntity blockEntity : blockEntities) {
                if (blockEntity instanceof NEBlockEntity<?, ?>neBlockEntity) {
                    neBlockEntity.updateCluster(null);
                }
            }
        } finally {
            MBCalculator.resumeGridRebuilds();
            if (ownsModification) {
                MBCalculator.setModificationInProgress(null);
            }
        }
    }
}
