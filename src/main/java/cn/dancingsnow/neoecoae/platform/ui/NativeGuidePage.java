package cn.dancingsnow.neoecoae.platform.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.TextWidget;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Open GuideNH when installed, retaining a bundled text fallback for standalone clients. */
public final class NativeGuidePage {

    private NativeGuidePage() {}

    @SideOnly(Side.CLIENT)
    public static void open(String page, net.minecraft.entity.player.EntityPlayer player, Runnable fallback) {
        if (Loader.isModLoaded("guidenh")) NativeGuideNh.open(page, player);
        else fallback.run();
    }

    public static ModularPanel create(String page, IPanelHandler handler) {
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_guide_" + page, 260, 220);
        String language = cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient() ? clientLanguage() : "en_US";
        String folder = language.toLowerCase(java.util.Locale.ROOT)
            .startsWith("zh_") ? "_zh_cn/" : "";
        String path = "/assets/neoecoae/ae2guide/" + folder + "neoecoae_intro/" + page + ".md";
        var stream = NativeGuidePage.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalArgumentException("Missing bundled guide " + path);
        String text;
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            text = reader.lines()
                .collect(Collectors.joining("\n"))
                .replaceAll("(?s)^---.*?---\\s*", "")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replace("**", "")
                .replace("`", "");
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
        panel.child(
            new ScrollWidget<>(new VerticalScrollData()).pos(8, 22)
                .size(244, 190)
                .child(
                    new TextWidget<>(text).width(232)
                        .textAlign(Alignment.TopLeft)
                        .color(0xff403e53)
                        .shadow(false)));
        panel.child(
            com.cleanroommc.modularui.widgets.ButtonWidget.panelCloseButton()
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    handler.closePanel();
                    return true;
                }));
        return panel;
    }

    @SideOnly(Side.CLIENT)
    private static String clientLanguage() {
        return net.minecraft.client.Minecraft.getMinecraft().gameSettings.language;
    }
}
