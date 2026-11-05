package cn.dancingsnow.neoecoae.platform.forge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class NativeKeyReuseClientAcceptance {
    private int menuTicks;
    private int playerTicks;
    private boolean launched;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        if (!launched && client.currentScreen instanceof GuiMainMenu && ++menuTicks == 20) {
            launched = true;
            client.launchIntegratedServer("key-reuse-acceptance", "Key Reuse Acceptance",
                new WorldSettings(1L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
        }
        if (client.thePlayer == null || client.theWorld == null || ++playerTicks != 60) return;
        String result = new String(Files.readAllBytes(java.nio.file.Paths.get("key-reuse-acceptance-client.txt")), StandardCharsets.UTF_8);
        if (!result.startsWith("PASS")) throw new AssertionError(result);
        Files.write(java.nio.file.Paths.get("key-reuse-client-world.txt"), "PASS: player joined and immutable keys reused\n".getBytes(StandardCharsets.UTF_8));
        cn.dancingsnow.neoecoae.NeoECOAE.LOG.info("KEY_REUSE_CLIENT_WORLD PASS");
        client.shutdown();
    }
}
