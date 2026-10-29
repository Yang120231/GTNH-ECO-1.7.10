package cn.dancingsnow.neoecoae.platform.ui.computation;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeMachineWidgets;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;

/** Source computation panel, synchronized task selector and exact CPU status pages. */
public final class NativeComputationPages {

    private static final String WIDGETS = "neoecoae:textures/guis/cpu_widget.png";

    private NativeComputationPages() {}

    public static ModularPanel create(ECOComputationSystemBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        NativeComputationSession session = new NativeComputationSession(host);
        sync.syncValue("computation.controller", session);
        var guide = sync.syncedPanel(
            "computation.guide",
            true,
            (panelSync, handler) -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage
                .create("computation_system", handler));
        NativePageLayout layout = new NativePageLayout("computation/controller");
        ModularPanel panel = cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel
            .create("neoecoae_computation_controller", layout.width(), layout.height())
            .background(IDrawable.EMPTY);
        ComputationView view = new ComputationView(panel, session);
        layout.children(panel, node -> {
            String custom = node.get("name")
                .getAsString();
            switch (custom) {
                case "computation_artwork":
                    return new NativeComputationArtwork(session, host.getItemFromBlockEntity());
                case "computation_drive_grid":
                    return drives(session);
                case "computation_switch":
                    int action = node.get("key")
                        .getAsString()
                        .equals("substitutions") ? 0
                            : node.get("key")
                                .getAsString()
                                .equals("cycle_planning") ? 1 : 2;
                    String field = action == 0 ? "substitutions" : action == 1 ? "cycles" : "fast";
                    return NativeMachineWidgets.toggle(
                        WIDGETS,
                        128,
                        () -> session.state()
                            .getBoolean(field),
                        () -> session.action(action, 0),
                        () -> Component.translatable(
                            "gui.neoecoae.computation.settings." + node.get("key")
                                .getAsString()));
                case "computation_toolbar":
                    return cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.left(
                        false,
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(
                            176,
                            0,
                            () -> Component.translatable("gui.neoecoae.guide"),
                            () -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage
                                .open("computation_system", player, guide::openPanel)),
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(
                            160,
                            16,
                            () -> text(
                                "selection",
                                session.state()
                                    .getString("selectionMode")),
                            () -> session.action(3, 0)),
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.networkFrequency(
                            () -> session.state()
                                .contains("frequency")
                                    ? session.state()
                                        .getInt("frequency")
                                    : -1,
                            delta -> session.action(9, delta)),
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar
                            .multilineButton(
                                () -> com.cleanroommc.modularui.drawable.UITexture.fullImage(
                                    "neoecoae:textures/gui/computation/eco_batching" + (session.state()
                                        .getBoolean("smartDoubling") ? "" : "_off") + ".png"),
                                () -> smartDoublingTooltip(
                                    session.state()
                                        .getBoolean("smartDoubling")),
                                () -> session.action(15, 0))
                            .name("computation-smart-doubling"),
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(
                            () -> com.cleanroommc.modularui.drawable.UITexture.fullImage(
                                "neoecoae:textures/gui/widget/cpu_selector" + (view.list ? "" : "_closed") + ".png"),
                            () -> text("tasks"),
                            () -> {
                                view.list = !view.list;
                                view.resize();
                            })
                            .name("computation-cpu-list-toggle"));
                case "computation_info":
                    return new com.cleanroommc.modularui.widget.ParentWidget<>().pos(251, 0)
                        .size(23, 30)
                        .background(
                            com.cleanroommc.modularui.drawable.UITexture
                                .fullImage("neoecoae:textures/gui/common/side_bar/mirrored_button_slot_up.png"))
                        .child(
                            new com.cleanroommc.modularui.widget.Widget<>().name("computation-info-icon")
                                .pos(0, 3)
                                .size(21, 21)
                                .background(
                                    com.cleanroommc.modularui.drawable.UITexture
                                        .fullImage("neoecoae:textures/gui/widget/information.png"))
                                .hoverBackground(
                                    com.cleanroommc.modularui.drawable.UITexture
                                        .fullImage("neoecoae:textures/gui/widget/information_highlight.png"))
                                .tooltip(tooltip -> tooltip.setAutoUpdate(true))
                                .tooltipDynamic(lines -> {
                                    CompoundTag state = session.state();
                                    if (state.getBoolean("endgameActive")) {
                                        lines.add(
                                            IKey.lang("gui.neoecoae.computation.endgame.active")
                                                .color(0xFF55FF8A));
                                        return;
                                    }
                                    lines.add(
                                        IKey.lang("gui.neoecoae.computation.endgame.title")
                                            .color(0xFFD8B4FE))
                                        .newLine();
                                    var blockers = state.getList("endgameBlockers", 8);
                                    for (int i = 0; i < blockers.size(); i++) {
                                        String key = blockers.getString(i);
                                        IKey reason = "host_count".equals(key) ? IKey.lang(
                                            "gui.neoecoae.computation.endgame.reason.host_count",
                                            Math.max(1, state.getInt("networkHosts")),
                                            8) : IKey.lang("gui.neoecoae.computation.endgame.reason." + key);
                                        lines.add(reason.color(0xFFFF5555))
                                            .newLine();
                                    }
                                }))
                        .setEnabledIf(widget -> !view.list);
                case "computation_cpu_selector":
                    return NativeCpuPages.selector(session, token -> {
                        view.status = true;
                        session.action(13, 9);
                        view.resize();
                    })
                        .setEnabledIf(widget -> view.list && !view.status);
                case "computation_status":
                    return NativeCpuPages.status(session, () -> {
                        view.status = false;
                        session.action(13, 6);
                        view.resize();
                    })
                        .setEnabledIf(widget -> view.status);
                default:
                    throw new IllegalArgumentException("Unknown source computation node: " + custom);
            }
        }, () -> com.cleanroommc.modularui.widgets.SlotGroupWidget.playerInventory(
            (index, slot) -> new cn.dancingsnow.neoecoae.platform.ui.NativeQuickMoveSlot(
                cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity::isSupportedCell,
                () -> session.action(19, index)).background(IDrawable.EMPTY)));
        return panel;
    }

    private static final class ComputationView {

        private final ModularPanel panel;
        private final NativeComputationSession session;
        boolean list, status;

        ComputationView(ModularPanel panel, NativeComputationSession session) {
            this.panel = panel;
            this.session = session;
        }

        void resize() {
            panel.getChildren()
                .get(0)
                .setEnabled(!status);
            panel.size(status ? 326 : list ? 362 : 295, status ? 254 : 231);
            panel.scheduleResize();
        }
    }

    private static com.cleanroommc.modularui.widget.ParentWidget<?> drives(NativeComputationSession session) {
        var grid = new com.cleanroommc.modularui.widget.ParentWidget<>().size(196, 52)
            .background(IDrawable.EMPTY);
        for (int index = 0; index < 22; index++) {
            final int cell = index;
            grid.child(
                new cn.dancingsnow.neoecoae.platform.ui.NativeSlotButton<>().slotInset(0)
                    .highlightWhen(
                        () -> session.drive(cell)
                            .getInt("token") != 0)
                    .onMousePressed(button -> {
                        if ((button != 0 && button != 1) || session.drive(cell)
                            .getInt("token") == 0) return false;
                        session.action(
                            Interactable.hasShiftDown() ? 11 : 10,
                            session.drive(cell)
                                .getInt("token"));
                        return true;
                    })
                    .child(
                        NativeMachineWidgets
                            .item(
                                () -> ItemStacks.parseOptional(
                                    RegistryAccess.EMPTY,
                                    session.drive(cell)
                                        .getCompound("item")))
                            .size(16))
                    .pos(index % 11 * 18, index / 11 * 36)
                    .size(16));
        }
        return grid;
    }

    static java.util.List<Component> smartDoublingTooltip(boolean on) {
        return java.util.Arrays.asList(
            Component.translatable("gui.neoecoae.crafting.smart_doubling"),
            Component.translatable("gui.neoecoae.computation.ui.switch." + (on ? "on" : "off"))
                .withColor(on ? 0x55FF55 : 0xAAAAAA),
            Component.translatable("gui.neoecoae.computation.ui.switch.smart_doubling.desc")
                .withColor(0xAAAAAA));
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.native_computation." + key, arguments);
    }
}
