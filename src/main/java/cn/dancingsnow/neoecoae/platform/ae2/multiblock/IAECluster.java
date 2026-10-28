package cn.dancingsnow.neoecoae.platform.ae2.multiblock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridHost;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Typed modern cluster contract projected into the actual GTNH multiblock proxy. */
public interface IAECluster extends appeng.me.cluster.IAECluster {

    BlockPos getBoundsMin();

    BlockPos getBoundsMax();

    Iterator<? extends TileEntity> getBlockEntities();

    /** Modern AE2's teardown flag; rv3's cluster contract has no equivalent. */
    default boolean isDestroyed() {
        return false;
    }

    @Override
    default Iterator<IGridHost> getTiles() {
        List<IGridHost> hosts = new ArrayList<>();
        getBlockEntities().forEachRemaining(tile -> { if (tile instanceof IGridHost) hosts.add((IGridHost) tile); });
        return hosts.iterator();
    }
}
