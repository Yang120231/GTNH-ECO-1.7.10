package cn.dancingsnow.neoecoae.platform.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.world.WorldEvent;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class StorageLifecycle {

    @SubscribeEvent
    public void save(WorldEvent.Save event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0)
            NativeWorldStorage.save(MinecraftServer.getServer());
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (!event.world.isRemote)
            cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning.unload(event.world);
        if (!event.world.isRemote) cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager.clear(
            (cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView) cn.dancingsnow.neoecoae.platform.minecraft.WorldView
                .of(event.world));
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) {
            MinecraftServer server = MinecraftServer.getServer();
            NativeWorldStorage.close(server);
            ECOInfiniteStorageDomains.onServerStopped(server);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            cn.dancingsnow.neoecoae.platform.network.ServerTasks.tick();
            cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning.tick();
            PortingFoundationFixture.tick();
            PortingStorageHostFixture.tick();
            PortingPlanningFixture.tick();
            for (net.minecraft.world.WorldServer world : net.minecraftforge.common.DimensionManager.getWorlds())
                cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager.tick(
                    (cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView) cn.dancingsnow.neoecoae.platform.minecraft.WorldView
                        .of(world));
            ECOCellMutationBatch.retry();
            ECOCellMutationBatch.assertClean();
        }
    }

    public static void stopped() {
        MinecraftServer server = MinecraftServer.getServer();
        cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOPlanning.clear();
        cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager.clearAll();
        ECOCellMutationBatch.clearThreadState();
        ECOCellMutationBatch.drainRetries();
        NativeWorldStorage.close(server);
        ECOInfiniteStorageDomains.onServerStopped(server);
        ECOStorageCells.clearRuntimeState();
    }
}
