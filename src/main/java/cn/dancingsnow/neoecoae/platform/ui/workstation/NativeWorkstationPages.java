package cn.dancingsnow.neoecoae.platform.ui.workstation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemHandler;
import cn.dancingsnow.neoecoae.platform.ui.NativeOwnedFluidSync;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Source 176x190 workstation coordinates with real native inventories, tanks, upgrades and output settings. */
public final class NativeWorkstationPages {

    private static final String ATLAS = "neoecoae:textures/gui/vendor/ae2/states.png";
    private static final String WIDGET = "neoecoae:textures/guis/workstation/integrated_working_station_widget.png";

    private NativeWorkstationPages() {}

    public static ModularPanel create(ECOIntegratedWorkingStationBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        NativePageLayout layout = new NativePageLayout("workstation/integrated");
        NativeWorkstationSession session = new NativeWorkstationSession(host);
        sync.syncValue("workstation.state", session);
        ModularPanel panel = ModularPanel
            .defaultPanel("neoecoae_integrated_workstation", layout.width(), layout.height())
            .background(
                NativePageLayout
                    .texture("neoecoae:textures/guis/workstation/integrated_working_station.png", 0, 0, 176, 190));
        panel.child(
            new NativeText(() -> Component.translatable("block.neoecoae.integrated_working_station")).pos(8, 6)
                .size(160, 10));
        NativeItemHandler input = new NativeItemHandler(host.getInputInv());
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) panel.child(
            new ItemSlot().slot(new ModularSlot(input, row + column * 3))
                .background((IDrawable) null)
                .pos(35 + column * 18, 23 + row * 18));
        panel.child(
            new ItemSlot().slot(new ModularSlot(new NativeItemHandler(host.getOutputInv()), 0).canPut(false))
                .background((IDrawable) null)
                .pos(122, 42));
        for (int index = 0; index < 4; index++) panel.child(
            new ItemSlot().slot(new ModularSlot(new InvWrapper(host.getUpgrades()), index))
                .pos(177, 4 + index * 18)
                .overlay(NativePageLayout.texture(ATLAS, 240, 208, 16, 16)));

        panel.child(
            new Gauge().syncHandler(new NativeOwnedFluidSync(host, session.tank(true), true, true))
                .alwaysShowFull(false)
                .contentPadding(0)
                .background((IDrawable) null)
                .pos(8, 23)
                .size(16, 54));
        panel.child(
            new Gauge().syncHandler(new NativeOwnedFluidSync(host, session.tank(false), false, true))
                .alwaysShowFull(false)
                .contentPadding(0)
                .background((IDrawable) null)
                .pos(152, 23)
                .size(16, 54));
        panel.child(
            clear(session::clearInput).pos(10, 81)
                .size(12, 11));
        panel.child(
            clear(session::clearOutput).pos(154, 81)
                .size(12, 11));
        panel.child(
            new Progress(session).pos(123, 68)
                .size(16, 3));
        panel.child(
            new NativeText(() -> Component.translatable("container.inventory")).pos(8, 94)
                .size(160, 10));
        NativeNetworkToolbox toolbox = NativeNetworkToolbox.find(player);
        panel.child(SlotGroupWidget.playerInventory((index, slot) -> {
            if (toolbox != null && index == toolbox.playerSlot()) slot = new LockedToolSlot();
            return slot.background((IDrawable) null);
        })
            .pos(7, 105));
        if (toolbox != null) toolbox.attach(panel);

        IPanelHandler outputs = sync
            .syncedPanel("workstation.outputs", true, (panelSync, handler) -> outputPanel(session, handler));
        panel.child(
            new Export(session).pos(-20, 24)
                .size(18, 20));
        panel.child(
            sideButton(UITexture.fullImage("neoecoae:textures/gui/widget/outputs.png"), outputs::openPanel).pos(-20, 44)
                .size(18, 20)
                .tooltipDynamic(t -> t.add(text("allow_outputs").getString())));
        return panel;
    }

    private static ButtonWidget<?> clear(Runnable action) {
        return new ButtonWidget<>().background(NativePageLayout.texture(WIDGET, 0, 6, 12, 11))
            .hoverBackground(NativePageLayout.texture(WIDGET, 12, 6, 12, 11))
            .onMousePressed(button -> {
                if (button != 0) return false;
                action.run();
                return true;
            });
    }

    private static ButtonWidget<?> sideButton(IDrawable icon, Runnable action) {
        return new ButtonWidget<>().background(NativePageLayout.texture(ATLAS, 176, 128, 18, 20))
            .overlay(icon)
            .onMousePressed(button -> {
                if (button != 0) return false;
                action.run();
                return true;
            });
    }

    private static Component text(String suffix) {
        return Component.translatable("gui.neoecoae.integrated_working_station." + suffix);
    }

    private static ModularPanel outputPanel(NativeWorkstationSession session, IPanelHandler handler) {
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_workstation_outputs", 128, 120);
        panel.child(
            new NativeText(() -> text("allow_outputs")).pos(7, 5)
                .size(98, 12));
        panel.child(
            new ButtonWidget<>().overlay(GuiTextures.CROSS_TINY)
                .pos(108, 3)
                .size(16)
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    handler.closePanel();
                    return true;
                }));
        RelativeSide[] uiSides = { RelativeSide.TOP, RelativeSide.LEFT, RelativeSide.FRONT, RelativeSide.RIGHT,
            RelativeSide.BOTTOM, RelativeSide.BACK };
        int[] xs = { 44, 12, 44, 76, 44, 76 }, ys = { 23, 52, 52, 52, 81, 81 };
        for (int index = 0; index < uiSides.length; index++) {
            RelativeSide shown = uiSides[index];
            RelativeSide actual = internalSide(shown);
            panel.child(
                new OutputSide(session, shown, actual).pos(xs[index], ys[index])
                    .size(24));
        }
        return panel;
    }

    public static RelativeSide internalSide(RelativeSide uiSide) {
        return uiSide == RelativeSide.LEFT ? RelativeSide.RIGHT
            : uiSide == RelativeSide.RIGHT ? RelativeSide.LEFT : uiSide;
    }

    private static final class OutputSide extends ButtonWidget<OutputSide> {

        private final NativeWorkstationSession session;
        private final RelativeSide actual;

        OutputSide(NativeWorkstationSession session, RelativeSide shown, RelativeSide actual) {
            this.session = session;
            this.actual = actual;
            onMousePressed(button -> {
                if (button != 0) return false;
                session.toggleSide(actual);
                return true;
            });
            tooltipDynamic(
                t -> t.add(
                    Component.translatable(
                        "gui.neoecoae.relative_side." + shown.name()
                            .toLowerCase(java.util.Locale.ROOT))
                        .getString())
                    .add(
                        text(session.outputAllowed(actual) ? "allow_outputs.enabled" : "allow_outputs.disabled")
                            .getString()));
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            UITexture.fullImage(
                "neoecoae:textures/gui/common/button" + (session.outputAllowed(actual) ? "_highlighted" : "") + ".png")
                .draw(0, 0, 24, 24);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            ItemStack adjacent = session.adjacentItem(actual);
            if (!ItemStacks.isEmpty(adjacent)) GuiDraw.drawItem(adjacent, 4, 4, 16, 16, context.getCurrentDrawingZ());
        }
    }

    private static final class Gauge extends FluidSlot {

        @Override
        @SideOnly(Side.CLIENT)
        public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            super.drawOverlay(context, theme);
            if (getFluidStack() == null && !isHovering()) return;
            for (int y = 5; y < getArea().height; y += 5)
                GuiDraw.drawRect(16 - ((y - 5) % 10 == 0 ? 5 : 3), y, (y - 5) % 10 == 0 ? 5 : 3, 1, 0xfff2f2f2);
        }
    }

    private static final class Progress extends Widget<Progress> {

        private final NativeWorkstationSession session;

        Progress(NativeWorkstationSession session) {
            this.session = session;
            tooltipDynamic(
                t -> t.add(
                    String.format(
                        java.util.Locale.ROOT,
                        "%.0f%%",
                        Math.min(1, session.progress() / (double) session.maxProgress()) * 100)));
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int width = Math.max(0, Math.min(16, 16 * session.progress() / session.maxProgress()));
            if (width > 0) NativePageLayout.texture(WIDGET, 0, 0, width, 3)
                .draw(0, 0, width, 3);
        }
    }

    private static final class Export extends ButtonWidget<Export> {

        private final NativeWorkstationSession session;

        Export(NativeWorkstationSession session) {
            this.session = session;
            background(NativePageLayout.texture(ATLAS, 176, 128, 18, 20));
            onMousePressed(button -> {
                if (button != 0) return false;
                session.toggleExport();
                return true;
            });
            tooltipDynamic(
                t -> t.add(
                    Component.translatable("gui.neoecoae.workstation.auto_export")
                        .getString())
                    .add(
                        Component
                            .translatable(
                                "gui.neoecoae.workstation.auto_export." + (session.autoExport() ? "on" : "off"))
                            .getString()));
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            NativePageLayout.texture(ATLAS, session.autoExport() ? 128 : 112, 96, 16, 16)
                .draw(1, 2, 16, 16);
        }
    }

    private static final class LockedToolSlot extends ItemSlot {

        @Override
        public void onInit() {
            super.onInit();
            getSlot().accessibility(false, false);
        }
    }
}
