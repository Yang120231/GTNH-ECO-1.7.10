package cn.dancingsnow.neoecoae.platform.forge;

import java.math.BigInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cn.dancingsnow.neoecoae.platform.ui.storage.NativeStorageSession;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Explicit development acceptance, in a new disposable save. It exercises actual client/server MUI packets. */
@SideOnly(Side.CLIENT)
public final class NativeClientAcceptance {

    private final String worldName = "ECO_Storage_Acceptance_20261025_" + System.currentTimeMillis();
    private int stage, ticks, stageTick;
    private volatile EntityPlayerMP serverPlayer;
    private volatile Throwable serverFailure;
    private boolean finished;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("neoecoae.clientAcceptance")) return;
        Minecraft client = Minecraft.getMinecraft();
        ticks++;
        try {
            if (serverFailure != null) throw new IllegalStateException("Server acceptance failed", serverFailure);
            if (ticks - stageTick > 2400 && !finished)
                throw new IllegalStateException("Client acceptance timed out at stage " + stage);
            if (stage == 0 && client.currentScreen instanceof GuiMainMenu) {
                com.cleanroommc.modularui.ModularUIConfig.guiDebugMode = false;
                if (new java.io.File(client.mcDataDir, "saves/" + worldName).exists())
                    throw new IllegalStateException("Acceptance save already exists");
                client.launchIntegratedServer(
                    worldName,
                    worldName,
                    new WorldSettings(9251025L, WorldSettings.GameType.CREATIVE, true, false, WorldType.FLAT)
                        .enableCommands());
                advance(1);
            } else if (stage == 1 && client.theWorld != null
                && client.thePlayer != null
                && client.getIntegratedServer() != null) {
                    advance(2);
                    ServerTasks.execute(() -> {
                        try {
                            var server = client.getIntegratedServer();
                            serverPlayer = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
                            serverPlayer.setGameType(WorldSettings.GameType.CREATIVE);
                            serverPlayer.capabilities.isFlying = true;
                            serverPlayer.sendPlayerAbilities();
                            serverPlayer.setPositionAndUpdate(12.5, 240, .5);
                            serverPlayer.setLocationAndAngles(12.5, 240, .5, 0, 0);
                            server.worldServerForDimension(0)
                                .getGameRules()
                                .setOrCreateGameRule("doDaylightCycle", "false");
                            server.worldServerForDimension(0)
                                .setWorldTime(6000);
                            PortingStorageHostFixture.startPreview(server.worldServerForDimension(0), serverPlayer);
                        } catch (Throwable error) {
                            serverFailure = error;
                        }
                    });
                } else if (stage == 2 && PortingStorageHostFixture.previewReady()) {
                    advance(3);
                    ServerTasks.execute(() -> {
                        try {
                            com.cleanroommc.modularui.factory.GuiFactories.tileEntity()
                                .open(serverPlayer, 12, 240, 2);
                        } catch (Throwable error) {
                            serverFailure = error;
                        }
                    });
                } else if (stage == 3 && ticks - stageTick > 40) {
                    NativeStorageSession session = NativeStorageSession.acceptanceInspection();
                    if (session == null || session.state()
                        .cellCount() == 0) return;
                    require(
                        session.state()
                            .formed()
                            && session.state()
                                .cellCount() == 12
                            && session.state()
                                .cells()
                                .size() == 33,
                        "Actual client page snapshot");
                    require(
                        session.state()
                            .totalBytes()
                            .equals(
                                BigInteger.valueOf(12)
                                    .shiftLeft(28)),
                        "Actual client capacity remains exact");
                    screenshot(client);
                    require(session.click(0, false), "Send actual source extraction action");
                    advance(4);
                } else if (stage == 4 && ticks - stageTick > 20) {
                    NativeStorageSession session = NativeStorageSession.acceptanceInspection();
                    if (session.state()
                        .cells()
                        .get(0)
                        .installed()) return;
                    require(
                        client.thePlayer.inventory.getItemStack() != null,
                        "Extracted physical disk reaches the client cursor");
                    screenshot(client);
                    require(session.click(0, false), "Send actual source insertion action");
                    advance(5);
                } else if (stage == 5 && ticks - stageTick > 20) {
                    NativeStorageSession session = NativeStorageSession.acceptanceInspection();
                    if (!session.state()
                        .cells()
                        .get(0)
                        .installed()) return;
                    require(
                        client.thePlayer.inventory.getItemStack() == null,
                        "Insertion consumes client cursor exactly once");
                    screenshot(client);
                    NeoECOAE.LOG.info(
                        "NEOECOAE_CLIENT_STORAGE_OK core_items={} physical_drives=12 page=33 exact_capacity={} native_extract_insert=true save={}",
                        NativeContent.items()
                            .size(),
                        session.state()
                            .totalBytes(),
                        worldName);
                    finish(client, true);
                } else if (finished && ticks - stageTick > 20) client.shutdown();
        } catch (Throwable failure) {
            NeoECOAE.LOG.error("NEOECOAE_CLIENT_ACCEPTANCE_FAILED stage=" + stage, failure);
            try {
                screenshot(client);
            } catch (Throwable ignored) {
                NeoECOAE.LOG.error("Cannot capture acceptance failure screenshot", ignored);
            }
            finish(client, false);
        }
    }

    private void advance(int value) {
        stage = value;
        stageTick = ticks;
    }

    private void finish(Minecraft client, boolean success) {
        finished = true;
        advance(6);
        serverFailure = null;
        if (client.theWorld != null) {
            client.theWorld.sendQuittingDisconnectingPacket();
            client.loadWorld(null);
        }
        client.displayGuiScreen(new GuiMainMenu());
        NeoECOAE.LOG.info("NEOECOAE_CLIENT_ACCEPTANCE_FINISHED success={}", success);
    }

    private static void screenshot(Minecraft client) {
        var session = NativeStorageSession.acceptanceInspection();
        if (session != null) NeoECOAE.LOG.info(
            "NEOECOAE_CLIENT_PAGE types={} cells={} cursor={}",
            session.state()
                .types()
                .size(),
            session.state()
                .cellCount(),
            client.thePlayer.inventory.getItemStack());
        NeoECOAE.LOG.info(
            "NEOECOAE_CLIENT_SCREENSHOT {}",
            ScreenShotHelper
                .saveScreenshot(client.mcDataDir, client.displayWidth, client.displayHeight, client.getFramebuffer())
                .getUnformattedText());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
