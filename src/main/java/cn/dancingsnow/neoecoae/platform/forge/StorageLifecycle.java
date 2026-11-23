package cn.dancingsnow.neoecoae.platform.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import appeng.api.networking.IGridHost;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.MBCalculator;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class StorageLifecycle {

    @SubscribeEvent
    public void chunkTopologyChanged(ChunkEvent event) {
        if (!(event instanceof ChunkEvent.Load || event instanceof ChunkEvent.Unload)
            || !(WorldView.of(event.world) instanceof ServerWorldView level)
            || level.getServer()
                .isStopped())
            return;
        for (Object entry : event.getChunk().chunkTileEntityMap.values()) {
            if (!(entry instanceof TileEntity tile) || !(tile instanceof IGridHost)) continue;
            int column = tile.xCoord & 15;
            int row = tile.zCoord & 15;
            if (column == 0) refreshAcrossChunk(level, tile, Direction.WEST);
            else if (column == 15) refreshAcrossChunk(level, tile, Direction.EAST);
            if (row == 0) refreshAcrossChunk(level, tile, Direction.NORTH);
            else if (row == 15) refreshAcrossChunk(level, tile, Direction.SOUTH);
        }
    }

    private static void refreshAcrossChunk(ServerWorldView level, TileEntity tile, Direction side) {
        BlockPos adjacent = new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord).relative(side);
        if (level.getBlockEntity(adjacent) instanceof NEBlockEntity<?, ?>member && member.getCluster() != null)
            member.getCluster()
                .memberChanged(member);
    }

    @SubscribeEvent
    public void save(WorldEvent.Save event) {
        // Every dimension's chunks follow the overworld in one save pass, so their drives' cells are written here.
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) {
            ECOCellMutationBatch.flushTickDeferred();
            NativeWorldStorage.save(MinecraftServer.getServer());
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (!event.world.isRemote)
            cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider.clearPendingUpdates(event.world);
        if (!event.world.isRemote)
            cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning.unload(event.world);
        if (WorldView.of(event.world) instanceof ServerWorldView level) {
            MBCalculator.clearPending(level);
            cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager.clear(level);
        }
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) {
            MinecraftServer server = MinecraftServer.getServer();
            NativeWorldStorage.close(server);
            ECOInfiniteStorageDomains.onServerStopped(server);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) cn.dancingsnow.neoecoae.platform.network.ServerThread.capture();
        if (event.phase == TickEvent.Phase.END) {
            cn.dancingsnow.neoecoae.platform.network.ServerTasks.tick();
            cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning.tick();
            cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmTracker.tick();
            cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftingStatusTracker.tick();
            cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftingGraphSync.tick();
            ECOCellMutationBatch.flushTickDeferred();
            cn.dancingsnow.neoecoae.platform.ae2.grid.NativeCellArrayUpdates.flushTick();
            ECOCellMutationBatch.retry();
            ECOCellMutationBatch.assertClean();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tickMultiblocks(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (net.minecraft.world.WorldServer world : net.minecraftforge.common.DimensionManager.getWorlds()) {
            ServerWorldView level = (ServerWorldView) WorldView.of(world);
            MBCalculator.tick(level);
            cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager.tick(level);
        }
        cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider.flushProviderUpdates();
    }

    public static void stopped() {
        cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider.clearPendingUpdates(null);
        cn.dancingsnow.neoecoae.platform.gt.NativeProcessingMachines.clear();
        cn.dancingsnow.neoecoae.impl.storage.legacy.BetaStorageReader.clearCache();
        MinecraftServer server = MinecraftServer.getServer();
        cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning.clear();
        cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftConfirmTracker.clear();
        cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftingStatusTracker.clear();
        cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECORemoteGuiOpener.clear();
        cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECOCraftingGraphSync.clear();
        cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager.clearAll();
        MBCalculator.clearPending();
        ECOCellMutationBatch.flushTickDeferred();
        ECOCellMutationBatch.clearThreadState();
        ECOCellMutationBatch.drainRetries();
        cn.dancingsnow.neoecoae.platform.ae2.grid.NativeCellArrayUpdates.clear();
        cn.dancingsnow.neoecoae.platform.network.ServerThread.clear();
        NativeWorldStorage.close(server);
        ECOInfiniteStorageDomains.onServerStopped(server);
        ECOStorageCells.clearRuntimeState();
        cn.dancingsnow.neoecoae.platform.ae2.upload.PatternUploadService.stopped();
    }
}
