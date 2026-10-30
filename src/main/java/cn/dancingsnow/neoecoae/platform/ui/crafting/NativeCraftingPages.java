package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.NativeMachineWidgets;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;

/** Source controller artwork with live worker previews, cooling controls and physical pattern slots. */
public final class NativeCraftingPages {

    private static final String WIDGETS = "neoecoae:textures/guis/crafting_widgets.png";

    private NativeCraftingPages() {}

    public static ModularPanel create(ECOCraftingSystemBlockEntity host, EntityPlayer player, PanelSyncManager sync) {
        NativeCraftingSession session = new NativeCraftingSession(host);
        sync.syncValue("crafting.controller", session);
        var guide = sync.syncedPanel(
            "crafting.guide",
            true,
            (panelSync, handler) -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage
                .create("crafting_system", handler));
        NativePageLayout layout = new NativePageLayout("crafting/crafting_controller");
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_crafting_controller", layout.width(), layout.height())
            .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY);
        layout.children(panel, node -> {
            String custom = node.get("custom")
                .getAsString();
            if (custom.equals("crafting_artwork"))
                return new NativeCraftingArtwork(session, host.getItemFromBlockEntity());
            if (custom.equals("player_inventory")) return NativePageLayout.playerInventory();
            if (custom.equals("mode_button")) {
                boolean overclock = node.get("mode")
                    .getAsString()
                    .equals("overclock");
                return NativeMachineWidgets.toggle(
                    WIDGETS,
                    89,
                    () -> session.state()
                        .getBoolean(overclock ? "overclock" : "cooling"),
                    () -> session.action(overclock ? 0 : 1, 0),
                    () -> {
                        Component mode = Component.translatable(
                            "gui.neoecoae.crafting." + (overclock ? "overclock" : "active_cooling")
                                + "."
                                + (session.state()
                                    .getBoolean(overclock ? "overclock" : "cooling") ? "on" : "off"));
                        return session.state()
                            .getBoolean("modeBlocked")
                                ? mode.append("\n")
                                    .append(Component.translatable("gui.neoecoae.crafting.mode_switch_blocked"))
                                : mode;
                    });
            }
            throw new IllegalArgumentException("Unknown source crafting node: " + custom);
        });
        panel.child(
            cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.left(
                true,
                cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(
                    176,
                    0,
                    () -> Component.translatable("gui.neoecoae.guide"),
                    () -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage
                        .open("crafting_system", player, guide::openPanel)),
                cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.multilineButton(
                    () -> NativePageLayout.texture(
                        cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.ATLAS,
                        session.state()
                            .getBoolean("substitutionsIgnored") ? 80 : 64,
                        80,
                        16,
                        16),
                    () -> substitutionTooltip(
                        session.state()
                            .getBoolean("substitutionsIgnored"),
                        session.state()
                            .getInt("substitutionPatterns")),
                    () -> session.action(5, 0)),
                cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.networkFrequency(
                    () -> session.state()
                        .contains("frequency")
                            ? session.state()
                                .getInt("frequency")
                            : -1,
                    delta -> session.action(4, delta))));
        panel.child(
            new com.cleanroommc.modularui.widget.ParentWidget<>().pos(251, 0)
                .size(23, 30)
                .background(
                    com.cleanroommc.modularui.drawable.UITexture
                        .fullImage("neoecoae:textures/gui/common/side_bar/gray_mirrored_button_slot_up.png"))
                .child(
                    new com.cleanroommc.modularui.widget.Widget<>().pos(0, 3)
                        .size(21, 21)
                        .background(
                            com.cleanroommc.modularui.drawable.UITexture
                                .fullImage("neoecoae:textures/gui/widget/information.png"))
                        .hoverBackground(
                            com.cleanroommc.modularui.drawable.UITexture
                                .fullImage("neoecoae:textures/gui/widget/information_highlight.png"))
                        .tooltip(tooltip -> tooltip.setAutoUpdate(true))
                        .tooltipDynamic(lines -> {
                            lines.add(
                                Component.translatable(
                                    session.state()
                                        .getBoolean("connected") ? "gui.neoecoae.host.network.connected"
                                            : "gui.neoecoae.host.network.disconnected")
                                    .getString());
                            var status = session.state()
                                .getList("maintenance", 8);
                            for (int index = 0; index < status.size(); index++) lines.newLine()
                                .add(
                                    cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar
                                        .tooltipLine(Component.fromJson(status.getString(index))));
                        })));
        return panel;
    }

    public static ModularPanel patternBus(ECOCraftingPatternBusBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        int rows = (host.getInternalInventory()
            .size() + 8) / 9;
        var panel = ModularPanel.defaultPanel("neoecoae_crafting_pattern_bus", 170, 112 + rows * 18)
            .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.panel());
        panel.child(
            new NativeText(() -> Component.translatable("block.neoecoae.crafting_pattern_bus"), 1, 0x3f3d52).pos(4, 6)
                .size(162, 12));
        panel.child(
            new com.cleanroommc.modularui.widget.Widget<>().pos(3, 21)
                .size(164, rows * 18 + 2)
                .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.inventoryBorder()));
        cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.inventoryFrames(panel, 4, 29 + rows * 18);
        var handler = new cn.dancingsnow.neoecoae.platform.ui.NativeItemHandler(
            host.getInternalInventory()
                .toItemHandler());
        String patterns = cn.dancingsnow.neoecoae.platform.ui.NativeSlotGroups.register(
            sync,
            "crafting.pattern_bus",
            9,
            cn.dancingsnow.neoecoae.platform.ui.NativeSlotGroups.STORAGE_PRIORITY);
        for (int index = 0; index < host.getInternalInventory()
            .size(); index++)
            panel.child(
                new PatternSlot().slot(new ModularSlot(handler, index).slotGroup(patterns))
                    .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.slot())
                    .pos(4 + index % 9 * 18, 22 + index / 9 * 18));
        panel.child(
            SlotGroupWidget
                .playerInventory(
                    (index, slot) -> new NativeItemSlot()
                        .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.slot()))
                .pos(4, 29 + rows * 18));
        return panel;
    }

    private static final class PatternSlot extends NativeItemSlot {

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public void draw(com.cleanroommc.modularui.screen.viewport.ModularGuiContext context,
            com.cleanroommc.modularui.theme.WidgetThemeEntry<?> theme) {
            super.draw(context, theme);
            if (!getSlot().getHasStack())
                NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/states.png", 240, 128, 16, 16)
                    .draw(1, 1, 16, 16);
        }

        /** Encoded patterns show their primary output, like AE2's own pattern slots with Shift held. */
        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        protected net.minecraft.item.ItemStack getItemStackForRendering(net.minecraft.item.ItemStack stack,
            boolean dragging) {
            if (stack != null && stack.getItem() instanceof appeng.items.misc.ItemEncodedPattern pattern) {
                var output = pattern.getOutput(stack);
                if (output != null) return output;
            }
            return super.getItemStackForRendering(stack, dragging);
        }
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
            new NativeItemSlot().slot(new ModularSlot(handler, index))
                .pos(7 + index % columns * 18, 21 + index / columns * 18));
        panel.child(
            SlotGroupWidget.playerInventory((index, slot) -> new NativeItemSlot())
                .pos(7, 35 + rows * 18));
        return panel;
    }

    /** CraftingPlanningModeButton's tooltip: the action the click performs, then the grid's substitution patterns. */
    static java.util.List<Component> substitutionTooltip(boolean ignoring, int patterns) {
        return java.util.Arrays.asList(
            Component.translatable(
                ignoring ? "gui.neoecoae.crafting.planning.ignore_substitutions.on"
                    : "gui.neoecoae.crafting.planning.ignore_substitutions.off"),
            Component.translatable("gui.neoecoae.crafting.planning.substitution_pattern_count", Math.max(0, patterns)));
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.native_crafting." + key, arguments);
    }
}
