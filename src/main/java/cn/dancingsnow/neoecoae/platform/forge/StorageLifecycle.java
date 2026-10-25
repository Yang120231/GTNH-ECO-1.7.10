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
            PortingFoundationFixture.tick();
            PortingStorageHostFixture.tick();
            PortingPlanningFixture.tick();
            ECOCellMutationBatch.retry();
            ECOCellMutationBatch.assertClean();
        }
    }

    public static void stopped() {
        MinecraftServer server = MinecraftServer.getServer();
        ECOCellMutationBatch.clearThreadState();
        ECOCellMutationBatch.drainRetries();
        NativeWorldStorage.close(server);
        ECOInfiniteStorageDomains.onServerStopped(server);
        ECOStorageCells.clearRuntimeState();
    }
}
