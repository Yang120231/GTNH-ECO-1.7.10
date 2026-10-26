package cn.dancingsnow.neoecoae.platform.ui.computation;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeMachineWidgets;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeBuilderPages;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeBuilderSession;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeControllerBuilds;

/** Source computation panel, synchronized task selector and exact CPU status pages. */
public final class NativeComputationPages {

    private static final String WIDGETS = "neoecoae:textures/guis/cpu_widget.png";

    private NativeComputationPages() {}

    public static ModularPanel create(ECOComputationSystemBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        NativeComputationSession session = new NativeComputationSession(host);
        sync.syncValue("computation.controller", session);
        var construction = new NativeBuilderSession(NativeControllerBuilds.config(host, player, ECOMachineKind.COMPUTATION));
        sync.syncValue("computation.builder", construction);
        var builder = sync.syncedPanel("computation.builder.window", true,
            (panelSync, handler) -> NativeBuilderPages.create(construction, handler));
        var guide = sync.syncedPanel("computation.guide", true,
            (panelSync, handler) -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage.create("computation_system", handler));
        NativePageLayout layout = new NativePageLayout("computation/controller");
        ModularPanel panel = cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel.create("neoecoae_computation_controller", layout.width(), layout.height())
            .background(IDrawable.EMPTY);
        ComputationView view = new ComputationView(panel, session);
        layout.children(panel, node -> {
            String custom = node.get("name").getAsString();
            switch (custom) {
                case "computation_artwork": return new NativeComputationArtwork(session, host.getItemFromBlockEntity());
                case "computation_drive_grid": return drives(session);
                case "computation_switch":
                    int action = node.get("key").getAsString().equals("substitutions") ? 0
                        : node.get("key").getAsString().equals("cycle_planning") ? 1 : 2;
                    String field = action == 0 ? "substitutions" : action == 1 ? "cycles" : "fast";
                    return NativeMachineWidgets.toggle(WIDGETS, 128, () -> session.state().getBoolean(field),
                        () -> session.action(action, 0), () -> Component.translatable(
                            "gui.neoecoae.computation.settings." + node.get("key").getAsString()));
                case "computation_toolbar":
                    return cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.left(false,
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(176, 0,
                            () -> Component.translatable("gui.tooltips.appliedenergistics2.OpenGuide"), guide::openPanel),
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(
                            () -> com.cleanroommc.modularui.drawable.UITexture.fullImage("neoecoae:textures/gui/widget/multiblock.png"),
                            () -> Component.translatable("gui.neoecoae.multiblock.builder"), builder::openPanel),
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(160, 16,
                            () -> text("selection", session.state().getString("selectionMode")), () -> session.action(3, 0)),
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(16, 240,
                            () -> text("frequency", session.state().getInt("frequency")), () -> session.action(9, 1)),
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(
                            () -> com.cleanroommc.modularui.drawable.UITexture.fullImage(
                                "neoecoae:textures/gui/widget/cpu_selector" + (view.list ? "" : "_closed") + ".png"),
                            () -> text("tasks"), () -> { view.list = !view.list; view.resize(); }).name("computation-cpu-list-toggle"));
                case "computation_info":
                    return new com.cleanroommc.modularui.widget.Widget<>().pos(251, 0).size(23, 30)
                        .background(com.cleanroommc.modularui.drawable.UITexture.fullImage(
                            "neoecoae:textures/gui/common/side_bar/mirrored_button_slot_up.png"))
                        .overlay(com.cleanroommc.modularui.drawable.UITexture.fullImage("neoecoae:textures/gui/widget/information.png"))
                        .setEnabledIf(widget -> !view.list);
                case "computation_cpu_selector":
                    return NativeCpuPages.selector(session, token -> { view.status = true; session.action(13, 9); view.resize(); })
                        .setEnabledIf(widget -> view.list && !view.status);
                case "computation_status":
                    return NativeCpuPages.status(session, () -> { view.status = false; session.action(13, 6); view.resize(); })
                        .setEnabledIf(widget -> view.status);
                default: throw new IllegalArgumentException("Unknown source computation node: " + custom);
            }
        });
        return panel;
    }

    private static final class ComputationView {
        private final ModularPanel panel;
        private final NativeComputationSession session;
        boolean list, status;
        ComputationView(ModularPanel panel, NativeComputationSession session) { this.panel = panel; this.session = session; }
        void resize() {
            panel.getChildren().get(0).setEnabled(!status);
            panel.size(status ? 326 : list ? 362 : 295, status ? 254 : 231);
            panel.scheduleResize();
        }
    }

    private static com.cleanroommc.modularui.widget.ParentWidget<?> drives(NativeComputationSession session) {
        var grid = new com.cleanroommc.modularui.widget.ParentWidget<>().size(196, 52).background(IDrawable.EMPTY);
        for (int index = 0; index < 22; index++) {
            final int cell = index;
            grid.child(new ButtonWidget<>().background(IDrawable.EMPTY).hoverBackground(IDrawable.EMPTY)
                .onMousePressed(button -> {
                    if ((button != 0 && button != 1) || session.drive(cell).getInt("token") == 0) return false;
                    session.action(Interactable.hasShiftDown() ? 11 : 10, session.drive(cell).getInt("token"));
                    return true;
                }).child(NativeMachineWidgets.item(() -> ItemStacks.parseOptional(RegistryAccess.EMPTY,
                    session.drive(cell).getCompound("item"))).size(16))
                .pos(index % 11 * 18, index / 11 * 36).size(16));
        }
        return grid;
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.native_computation." + key, arguments);
    }
}
