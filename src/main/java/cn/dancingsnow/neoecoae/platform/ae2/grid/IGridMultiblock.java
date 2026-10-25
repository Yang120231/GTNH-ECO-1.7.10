package cn.dancingsnow.neoecoae.platform.ae2.grid;

import java.util.Iterator;

import appeng.api.networking.IGridNode;

/** Modern node service, supplied to the real GTNH multiblock IGridBlock proxy. */
@FunctionalInterface
public interface IGridMultiblock {

    Iterator<IGridNode> getMultiblockNodes();
}
