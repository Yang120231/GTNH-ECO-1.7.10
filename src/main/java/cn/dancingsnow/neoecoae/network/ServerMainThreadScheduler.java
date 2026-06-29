package cn.dancingsnow.neoecoae.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ServerMainThreadScheduler {

    private static final ServerMainThreadScheduler INSTANCE = new ServerMainThreadScheduler();
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();
    private static boolean registered;

    private ServerMainThreadScheduler() {}

    static void register() {
        if (registered) {
            return;
        }
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        registered = true;
    }

    static void schedule(Runnable task) {
        TASKS.add(task);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Runnable task;
        while ((task = TASKS.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                NeoECOAE.LOG.error("Failed to run scheduled server network task", e);
            }
        }
    }
}
