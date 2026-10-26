package cn.dancingsnow.neoecoae.platform.ui.multiblock;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Source builder controls/material table in a native draggable panel sized for the 1.7 client. */
public final class NativeBuilderPages {

    private NativeBuilderPages() {}

    public static ModularPanel create(NativeBuilderSession session, IPanelHandler handler) {
        ModularPanel panel = cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel.create("neoecoae_multiblock_builder", 286, 221)
            .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.panel());
        panel.child(new com.cleanroommc.modularui.widget.Widget<>().pos(4, 29).size(116, 188)
            .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.panel()));
        panel.child(new com.cleanroommc.modularui.widget.Widget<>().pos(124, 29).size(152, 104)
            .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.panel()));
        panel.child(
            label("builder").pos(8, 6)
                .size(240, 12));
        panel.child(
            new ButtonWidget<>().pos(264, 4)
                .size(16)
                .overlay(GuiTextures.CROSS_TINY)
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    handler.closePanel();
                    return true;
                }));
        panel.child(
            label("parameters").pos(7, 32)
                .size(110, 10));
        panel.child(
            button("-", () -> session.changeLength(-1)).pos(7, 45)
                .size(18)
                .setEnabledIf(widget -> !session.building()));
        panel.child(
            new NativeText(() -> text("length", session.length()), 1, 0x3f3d52).pos(27, 49)
                .size(54, 10));
        panel.child(
            button("+", () -> session.changeLength(1)).pos(83, 45)
                .size(18)
                .setEnabledIf(widget -> !session.building()));
        panel.child(
            label("mirror").pos(7, 71)
                .size(42, 10));
        panel.child(
            buttonKey("mirror.off", () -> session.mirror(false)).pos(51, 67)
                .size(32, 18)
                .setEnabledIf(widget -> !session.building() && session.mirrored()));
        panel.child(
            buttonKey("mirror.on", () -> session.mirror(true)).pos(85, 67)
                .size(32, 18)
                .setEnabledIf(widget -> !session.building() && !session.mirrored()));
        panel.child(
            label("live_result").pos(7, 89)
                .size(110, 10));
        String[] metrics = { "missing", "conflicts", "reused", "required_items" };
        String[] names = { "missing", "conflicts", "reused", "required" };
        for (int index = 0; index < metrics.length; index++) {
            final int value = index;
            panel.child(
                new NativeText(() -> text(metrics[value], session.metric(names[value])), 1, 0x3f3d52).pos(7, 102 + index * 13)
                    .size(110, 10));
        }
        panel.child(
            new NativeText(() -> text("status." + session.status()), 1, 0x3f3d52).pos(7, 154)
                .size(110, 9));
        panel.child(label("actions").pos(7, 167).size(110, 9));
        panel.child(
            buttonKey("build", session::build).pos(7, 180)
                .size(104, 20)
                .setEnabledIf(widget -> session.canBuild()));
        panel.child(
            label("auto_preview_hint").pos(7, 204)
                .size(110, 9));
        panel.child(
            label("materials").pos(127, 32)
                .size(150, 10));
        for (int index = 0; index < 24; index++) panel.child(
            new Material(session, index).pos(128 + index % 8 * 18, 47 + index / 8 * 18)
                .size(18));
        panel.child(
            label("conflict_preview").pos(127, 107)
                .size(150, 10));
        panel.child(
            new NativeText(() -> text("conflicts", session.metric("conflicts")), 1, 0x3f3d52).pos(127, 121)
                .size(150, 12)
                .tooltipDynamic(tooltip -> {
                    if (session.metric("conflicts") == 0) tooltip.add(text("no_conflicts").getString());
                    else {
                        tooltip.add(text("conflict_positions").getString());
                        tooltip.add(session.conflicts());
                        if (session.metric("otherConflicts") > 0)
                            tooltip.add(text("more_conflicts", session.metric("otherConflicts")).getString());
                    }
                }));
        return panel;
    }

    private static NativeText label(String key) {
        return new NativeText(() -> text(key), 1, 0x236f80);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.neoecoae.multiblock." + key, args);
    }

    private static ButtonWidget<?> button(String label, Runnable action) {
        return cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.button().child(new NativeText(() -> Component.literal(label)))
            .onMousePressed(button -> {
                if (button != 0) return false;
                action.run();
                return true;
            });
    }

    private static ButtonWidget<?> buttonKey(String key, Runnable action) {
        return cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.button().child(new NativeText(() -> text(key)))
            .onMousePressed(button -> {
                if (button != 0) return false;
                action.run();
                return true;
            });
    }

    private static final class Material extends ButtonWidget<Material> {

        private final NativeBuilderSession session;
        private final int index;

        Material(NativeBuilderSession session, int index) {
            this.session = session;
            this.index = index;
            tooltipDynamic(tooltip -> {
                var stack = session.material(index);
                if (!ItemStacks.isEmpty(stack)) tooltip.addFromItem(stack)
                    .newLine()
                    .add(session.available(index) + " / " + session.required(index));
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            UITexture.fullImage("neoecoae:textures/gui/common/slot.png")
                .draw(0, 0, 18, 18);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            var stack = session.material(index);
            if (ItemStacks.isEmpty(stack)) return;
            GuiDraw.drawItem(stack, 1, 1, 16, 16, context.getCurrentDrawingZ());
            com.cleanroommc.modularui.utils.Platform.setupDrawFont();
            var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
            String count = String.valueOf(session.required(index));
            org.lwjgl.opengl.GL11.glPushMatrix();
            try {
                org.lwjgl.opengl.GL11.glTranslatef(17 - font.getStringWidth(count) * .5f, 12, 100);
                org.lwjgl.opengl.GL11.glScalef(.5f, .5f, 1);
                font.drawStringWithShadow(count, 0, 0, session.enough(index) ? 0x55ff55 : 0xff5555);
            } finally {
                org.lwjgl.opengl.GL11.glPopMatrix();
            }
        }
    }
}
