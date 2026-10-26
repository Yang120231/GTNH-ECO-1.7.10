package cn.dancingsnow.neoecoae.platform.ui.computation;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemHandler;
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
        var construction = new NativeBuilderSession(
            NativeControllerBuilds.config(host, player, ECOMachineKind.COMPUTATION));
        sync.syncValue("computation.builder", construction);
        var builder = sync.syncedPanel(
            "computation.builder.window",
            true,
            (panelSync, handler) -> NativeBuilderPages.create(construction, handler));
        IPanelHandler detail = sync
            .syncedPanel("computation.status", true, (panelSync, handler) -> status(session, handler));
        IPanelHandler selector = sync
            .syncedPanel("computation.tasks", true, (panelSync, handler) -> tasks(session, detail, handler));
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_computation_controller", 253, 231)
            .background(NativePageLayout.texture("neoecoae:textures/guis/eco_cpu_controller.png", 0, 0, 253, 231));
        panel.child(
            new NativeText(() -> text("title")).pos(44, 9)
                .size(125, 12));
        panel.child(
            new NativeText(
                () -> Component.literal(
                    session.state()
                        .getString("tierName"))).pos(24, 9)
                            .size(15, 12));
        panel.child(
            new NativeText(
                () -> text(
                    session.state()
                        .getBoolean("formed") ? "formed" : "unformed")).pos(178, 151)
                            .size(65, 16));
        for (int index = 0; index < 22; index++) {
            final int cell = index;
            panel.child(
                new ButtonWidget<>().background((IDrawable) null)
                    .onMousePressed(button -> {
                        if (button != 0 || session.drive(cell)
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
                    .pos(49 + index % 11 * 18, 30 + index / 11 * 36)
                    .size(16));
        }
        panel.child(
            NativeMachineWidgets.button(
                () -> Component.literal("<"),
                () -> session.action(
                    12,
                    session.state()
                        .getInt("driveOffset") - 11))
                .pos(49, 85)
                .size(17, 12));
        panel.child(
            NativeMachineWidgets.button(
                () -> Component.literal(">"),
                () -> session.action(
                    12,
                    session.state()
                        .getInt("driveOffset") + 11))
                .pos(228, 85)
                .size(17, 12));
        for (int index = 0; index < 3; index++) {
            final int action = index;
            String key = index == 0 ? "substitutions" : index == 1 ? "cycles" : "fast";
            panel.child(
                NativeMachineWidgets.toggle(
                    WIDGETS,
                    128,
                    () -> session.state()
                        .getBoolean(key),
                    () -> session.action(action, 0),
                    () -> text(
                        key,
                        text(
                            session.state()
                                .getBoolean(key) ? "on" : "off")))
                    .pos(10 + index * 23, 113)
                    .size(15, 26));
        }
        panel.child(
            new NativeText(
                () -> text(
                    "bytes",
                    session.state()
                        .getLong("bytes")
                        - session.state()
                            .getLong("freeBytes"),
                    session.state()
                        .getLong("bytes")),
                .6f,
                0xe4e4ee).pos(83, 104)
                    .size(44, 30));
        panel.child(
            new NativeText(
                () -> text(
                    "threads",
                    session.state()
                        .getInt("cpuCount"),
                    session.state()
                        .getInt("threads")),
                .6f,
                0xe4e4ee).pos(140, 104)
                    .size(44, 30));
        panel.child(
            new NativeText(
                () -> text(
                    "parallel",
                    session.state()
                        .getInt("parallel")),
                .6f,
                0xe4e4ee).pos(197, 104)
                    .size(44, 30));
        panel.child(
            NativeMachineWidgets.button(
                () -> text(
                    "selection",
                    session.state()
                        .getString("selectionMode")),
                () -> session.action(3, 0))
                .pos(-20, 24)
                .size(18, 20));
        panel.child(
            NativeMachineWidgets.button(() -> text("tasks"), selector::openPanel)
                .pos(-20, 44)
                .size(18, 20));
        panel.child(
            NativeMachineWidgets.button(
                () -> text(
                    "frequency",
                    session.state()
                        .getInt("frequency")),
                () -> session.action(9, 1))
                .pos(-20, 64)
                .size(18, 20));
        panel.child(
            NativeMachineWidgets
                .button(() -> Component.translatable("gui.neoecoae.multiblock.builder"), builder::openPanel)
                .pos(-20, 84)
                .size(18, 20));
        panel.child(
            new NativeText(
                () -> text(
                    "frequency",
                    session.state()
                        .getInt("frequency")),
                .6f,
                0xe4e4ee).pos(178, 201)
                    .size(65, 16));
        panel.child(
            SlotGroupWidget.playerInventory(false)
                .pos(7, 146));
        return panel;
    }

    public static ModularPanel drive(ECOComputationDriveBlockEntity host, EntityPlayer player, PanelSyncManager sync) {
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_computation_drive", 176, 136);
        panel.child(
            new NativeText(() -> Component.translatable("block.neoecoae.computation_drive")).pos(7, 5)
                .size(162, 12));
        panel.child(
            new ItemSlot()
                .slot(new ModularSlot(new NativeItemHandler(host.getInternalInventory(), host::canExtractCell), 0))
                .pos(79, 22));
        panel.child(
            SlotGroupWidget.playerInventory(false)
                .pos(7, 50));
        return panel;
    }

    private static ModularPanel tasks(NativeComputationSession session, IPanelHandler detail, IPanelHandler handler) {
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_computation_tasks", 238, 204);
        panel.child(
            new NativeText(() -> text("tasks")).pos(8, 5)
                .size(200, 12));
        close(panel, handler, 215);
        for (int index = 0; index < NativeComputationSession.PAGE_SIZE; index++) {
            final int row = index;
            panel.child(NativeMachineWidgets.button(() -> {
                var cpu = session.cpu(row);
                return Component.literal(
                    cpu.getInt("token") == 0 ? ""
                        : cpu.getString("output") + "  "
                            + cpu.getString("completed")
                            + "/"
                            + cpu.getString("requested"));
            }, () -> {
                int token = session.cpu(row)
                    .getInt("token");
                if (token == 0) return;
                session.action(5, token);
                detail.openPanel();
            })
                .pos(8, 22 + index * 19)
                .size(222, 17));
        }
        panel.child(
            NativeMachineWidgets.button(
                () -> Component.literal("<"),
                () -> session.action(
                    4,
                    session.state()
                        .getInt("offset") - NativeComputationSession.PAGE_SIZE))
                .pos(8, 179)
                .size(30, 17));
        panel.child(
            new NativeText(
                () -> text(
                    "task_count",
                    session.state()
                        .getInt("offset") + 1,
                    session.state()
                        .getInt("cpuCount"))).pos(46, 179)
                            .size(146, 17));
        panel.child(
            NativeMachineWidgets.button(
                () -> Component.literal(">"),
                () -> session.action(
                    4,
                    session.state()
                        .getInt("offset") + NativeComputationSession.PAGE_SIZE))
                .pos(200, 179)
                .size(30, 17));
        return panel;
    }

    private static ModularPanel status(NativeComputationSession session, IPanelHandler handler) {
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_computation_status", 326, 254);
        panel.child(
            new NativeText(() -> Component.literal(selected(session).getString("output"))).pos(8, 6)
                .size(285, 12));
        close(panel, handler, 303);
        panel.child(
            new NativeText(
                () -> text(
                    "progress",
                    selected(session).getString("completed"),
                    selected(session).getString("requested"))).pos(8, 23)
                        .size(310, 13));
        panel.child(
            new NativeText(() -> text("remaining", selected(session).getString("remaining"))).pos(8, 38)
                .size(310, 13));
        panel.child(
            new NativeText(
                () -> text(
                    "elapsed",
                    selected(session).getLong("elapsed") / 1_000_000_000L,
                    selected(session).getLong("bytes"))).pos(8, 53)
                        .size(310, 13));
        panel.child(
            new NativeText(
                () -> Component.literal(
                    selected(session).getString("state") + " "
                        + selected(session).getString("waiting")
                        + " "
                        + selected(session).getString("error"))).pos(8, 68)
                            .size(310, 16));
        String[] sections = { "stored", "active", "pending" };
        for (int column = 0; column < sections.length; column++) {
            final String key = sections[column];
            panel.child(
                new NativeText(() -> text(key)).pos(8 + column * 105, 89)
                    .size(100, 12));
            for (int index = 0; index < 8; index++) {
                final int row = index;
                panel.child(new NativeText(() -> {
                    var items = selected(session).getList(key, 10);
                    if (row >= items.size()) return Component.empty();
                    var entry = items.getCompound(row);
                    return Component.literal(entry.getString("name") + " × " + entry.getString("amount"));
                }, .65f, 0x3f3d52).pos(8 + column * 105, 104 + index * 13)
                    .size(100, 12));
            }
        }
        panel.child(
            NativeMachineWidgets
                .button(
                    () -> text(selected(session).getBoolean("suspended") ? "resume" : "pause"),
                    () -> session.action(6, selected(session).getInt("token")))
                .pos(8, 224)
                .size(70, 20));
        panel.child(
            NativeMachineWidgets
                .button(() -> text("cancel"), () -> session.action(7, selected(session).getInt("token")))
                .pos(83, 224)
                .size(70, 20));
        panel.child(
            NativeMachineWidgets
                .button(
                    () -> Component.literal("<"),
                    () -> session.action(8, selected(session).getInt("itemOffset") - 8))
                .pos(248, 224)
                .size(30, 20));
        panel.child(
            NativeMachineWidgets
                .button(
                    () -> Component.literal(">"),
                    () -> session.action(8, selected(session).getInt("itemOffset") + 8))
                .pos(286, 224)
                .size(30, 20));
        return panel;
    }

    private static CompoundTag selected(NativeComputationSession session) {
        return session.state()
            .getCompound("selected");
    }

    private static void close(ModularPanel panel, IPanelHandler handler, int x) {
        panel.child(
            NativeMachineWidgets.button(() -> Component.literal("×"), handler::closePanel)
                .pos(x, 4)
                .size(16));
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.native_computation." + key, arguments);
    }
}
