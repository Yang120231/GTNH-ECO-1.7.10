package cn.dancingsnow.neoecoae.platform.ui;

import java.io.InputStream;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.util.StringTranslate;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Opt-in launch smoke check: wait for the real main menu and inspect the loaded client translations. */
@SideOnly(Side.CLIENT)
public final class NativeLaunchAcceptance {

    private int menuTicks;
    private boolean finished;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        if (!(client.currentScreen instanceof GuiMainMenu) || ++menuTicks < 40) return;
        finished = true;
        try (InputStream stream = getClass().getResourceAsStream("/assets/neoecoae/lang/en_US.lang")) {
            if (stream == null) throw new IllegalStateException("Missing English client translations");
            Map<String, String> english = StringTranslate.parseLangFile(stream);
            int checked = 0;
            for (String key : english.keySet()) {
                if (!key.startsWith("gui.neoecoae.")) continue;
                String translated = StatCollector.translateToLocal(key);
                if (translated.equals(key)) throw new IllegalStateException("Missing client translation: " + key);
                checked++;
            }
            for (int frequency = 1; frequency <= 4; frequency++) {
                String text = NativeHostSideBar.frequencyText(frequency)
                    .getString();
                if (!text.endsWith(": " + frequency) || text.contains("gui."))
                    throw new IllegalStateException("Missing frequency: " + text);
                NeoECOAE.LOG.info("NEOECOAE_LAUNCH_FREQUENCY {}", text);
            }
            NeoECOAE.LOG.info(
                "NEOECOAE_LAUNCH_SCREENSHOT {}",
                ScreenShotHelper
                    .saveScreenshot(
                        client.mcDataDir,
                        client.displayWidth,
                        client.displayHeight,
                        client.getFramebuffer())
                    .getUnformattedText());
            NeoECOAE.LOG.info(
                "NEOECOAE_LAUNCH_ACCEPTANCE_OK java={} language={} gui_keys={}",
                System.getProperty("java.version"),
                client.gameSettings.language,
                checked);
        } catch (Throwable error) {
            NeoECOAE.LOG.error("NEOECOAE_LAUNCH_ACCEPTANCE_FAILED", error);
        }
        client.shutdown();
    }
}
