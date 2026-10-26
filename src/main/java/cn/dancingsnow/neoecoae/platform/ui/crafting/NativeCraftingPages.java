package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.ui.NativeMachineWidgets;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeBuilderPages;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeBuilderSession;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeControllerBuilds;

/** Source controller artwork with live worker previews, cooling controls and physical pattern slots. */
public final class NativeCraftingPages {

    private static final String WIDGETS = "neoecoae:textures/guis/crafting_widgets.png";

    private NativeCraftingPages() {}

    public static ModularPanel create(ECOCraftingSystemBlockEntity host, EntityPlayer player, PanelSyncManager sync) {
        NativeCraftingSession session = new NativeCraftingSession(host);
        sync.syncValue("crafting.controller", session);
        var construction = new NativeBuilderSession(
            NativeControllerBuilds.config(host, player, ECOMachineKind.CRAFTING));
        sync.syncValue("crafting.builder", construction);
        var builder = sync.syncedPanel(
            "crafting.builder.window",
            true,
            (panelSync, handler) -> NativeBuilderPages.create(construction, handler));
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_crafting_controller", 253, 214)
            .background(NativePageLayout.texture("neoecoae:textures/guis/crafting_controller.png", 0, 0, 253, 214));
        panel.child(
            new NativeText(() -> text("title")).pos(44, 9)
                .size(124, 12));
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
                        .getBoolean("formed") ? "formed" : "unformed")).pos(177, 9)
                            .size(68, 12));
        panel.child(
            NativeMachineWidgets.toggle(
                WIDGETS,
                89,
                () -> session.state()
                    .getBoolean("overclock"),
                () -> session.action(0, 0),
                () -> Component.translatable(
                    "gui.neoecoae.crafting.overclock." + (session.state()
                        .getBoolean("overclock") ? "on" : "off")))
                .pos(37, 96)
                .size(15, 26));
        panel.child(
            NativeMachineWidgets.toggle(
                WIDGETS,
                89,
                () -> session.state()
                    .getBoolean("cooling"),
                () -> session.action(1, 0),
                () -> Component.translatable(
                    "gui.neoecoae.crafting.active_cooling." + (session.state()
                        .getBoolean("cooling") ? "on" : "off")))
                .pos(10, 96)
                .size(15, 26));
        for (int index = 0; index < 6; index++) {
            final int cell = index;
            panel.child(
                NativeMachineWidgets.item(
                    () -> ItemStacks.parseOptional(
                        RegistryAccess.EMPTY,
                        session.output(cell)
                            .getCompound("item")))
                    .pos(49 + index * 18, 38)
                    .size(16));
            panel.child(
                new NativeText(
                    () -> Component.literal(
                        session.output(cell)
                            .getString("amount")),
                    .5f,
                    0xe4e4ee).pos(49 + index * 18, 56)
                        .size(17, 8));
            panel.child(new NativeText(() -> {
                var output = session.output(cell);
                return Component.literal(
                    output.isEmpty() ? ""
                        : Math.min(100, 100L * output.getInt("progress") / Math.max(1, output.getInt("maxProgress")))
                            + "%");
            }, .5f, 0xb4b6cc).pos(49 + index * 18, 65)
                .size(17, 8));
        }
        panel.child(
            new NativeText(
                () -> text(
                    "threads",
                    session.state()
                        .getInt("busy"),
                    session.state()
                        .getInt("capacity")),
                .65f,
                0xb4e2ff).pos(65, 82)
                    .size(49, 14));
        panel.child(
            new NativeText(
                () -> text(
                    "workers",
                    session.state()
                        .getInt("workers")),
                .65f,
                0xffc2ed).pos(118, 82)
                    .size(49, 14));
        panel.child(
            new NativeText(
                () -> text(
                    "overclock",
                    session.state()
                        .getInt("effectiveOverclock"),
                    session.state()
                        .getInt("overclockTimes")),
                .65f,
                0xe4e4ee).pos(65, 98)
                    .size(102, 10));
        panel.child(
            NativeMachineWidgets.button(
                () -> Component.literal("<"),
                () -> session.action(
                    3,
                    session.state()
                        .getInt("offset") - 6))
                .pos(119, 110)
                .size(20, 13));
        panel.child(
            NativeMachineWidgets.button(
                () -> Component.literal(">"),
                () -> session.action(
                    3,
                    session.state()
                        .getInt("offset") + 6))
                .pos(144, 110)
                .size(20, 13));
        panel.child(
            new NativeText(
                () -> text(
                    "coolant",
                    session.state()
                        .getLong("coolant"),
                    session.state()
                        .getLong("coolantCapacity")),
                .6f,
                0xb4e2ff).pos(195, 80)
                    .size(50, 35));
        panel.child(
            new NativeText(
                () -> text(
                    "performance",
                    session.state()
                        .getLong("averageNanos") / 1000),
                .6f,
                0xe4e4ee).pos(196, 132)
                    .size(48, 20));
        panel.child(
            new NativeText(
                () -> text(
                    "patterns",
                    session.state()
                        .getInt("patternBuses")),
                .6f,
                0xe4e4ee).pos(196, 157)
                    .size(48, 20));
        panel.child(
            new NativeText(
                () -> text(
                    "parallel",
                    session.state()
                        .getInt("parallelCores")),
                .6f,
                0xe4e4ee).pos(196, 182)
                    .size(48, 20));
        panel.child(
            SlotGroupWidget.playerInventory(false)
                .pos(7, 129));
        panel.child(
            NativeMachineWidgets
                .button(() -> Component.translatable("gui.neoecoae.multiblock.builder"), builder::openPanel)
                .pos(-20, 24)
                .size(18, 20));
        panel.child(
            NativeMachineWidgets.button(
                () -> Component.literal(
                    "#" + session.state()
                        .getInt("frequency")),
                () -> session.action(4, 1))
                .pos(-20, 44)
                .size(18, 20));
        return panel;
    }

    public static ModularPanel patternBus(ECOCraftingPatternBusBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        return inventory(
            "neoecoae_crafting_pattern_bus",
            "block.neoecoae.crafting_pattern_bus",
            host.getInternalInventory(),
            9);
    }

    public static ModularPanel inventory(String id, String title, net.minecraft.inventory.IInventory inventory,
        int columns) {
        int rows = (inventory.getSizeInventory() + columns - 1) / columns;
        ModularPanel panel = ModularPanel.defaultPanel(id, Math.max(176, columns * 18 + 14), 118 + rows * 18);
        panel.child(
            new NativeText(() -> Component.translatable(title)).pos(7, 5)
                .size(162, 12));
        InvWrapper handler = new InvWrapper(inventory);
        for (int index = 0; index < inventory.getSizeInventory(); index++) panel.child(
            new ItemSlot().slot(new ModularSlot(handler, index))
                .pos(7 + index % columns * 18, 21 + index / columns * 18));
        panel.child(
            SlotGroupWidget.playerInventory(false)
                .pos(7, 35 + rows * 18));
        return panel;
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.native_crafting." + key, arguments);
    }
}
