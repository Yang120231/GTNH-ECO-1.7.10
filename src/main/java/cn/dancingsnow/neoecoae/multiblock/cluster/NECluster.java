package cn.dancingsnow.neoecoae.multiblock.cluster;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAECluster;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

public abstract class NECluster<T extends NECluster<T>> implements IAECluster {

    private final BlockPos boundMin;
    private final BlockPos boundMax;
    protected final List<TileEntity> blockEntities = new ArrayList<>();

    private boolean destroyed = false;

    public boolean isDestroyed() {
        return destroyed;
    }

    public NECluster(BlockPos boundMin, BlockPos boundMax) {
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
        try {
            for (TileEntity blockEntity : blockEntities) {
                if (blockEntity instanceof NEBlockEntity<?, ?>neBlockEntity) {
                    neBlockEntity.updateCluster(null);
                }
            }
        } finally {
            if (ownsModification) {
                MBCalculator.setModificationInProgress(null);
            }
        }
    }
}
