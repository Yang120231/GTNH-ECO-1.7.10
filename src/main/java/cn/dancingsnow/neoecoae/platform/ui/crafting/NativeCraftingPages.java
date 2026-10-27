package cn.dancingsnow.neoecoae.platform.ui.crafting;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemSlot;
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
        var construction = new NativeBuilderSession(NativeControllerBuilds.config(host, player, ECOMachineKind.CRAFTING));
        sync.syncValue("crafting.builder", construction);
        var builder = sync.syncedPanel("crafting.builder.window", true,
            (panelSync, handler) -> NativeBuilderPages.create(construction, handler));
        var guide = sync.syncedPanel("crafting.guide", true,
            (panelSync, handler) -> cn.dancingsnow.neoecoae.platform.ui.NativeGuidePage.create("crafting_system", handler));
        NativePageLayout layout = new NativePageLayout("crafting/crafting_controller");
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_crafting_controller", layout.width(), layout.height())
            .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY);
        layout.children(panel, node -> {
            String custom = node.get("custom").getAsString();
            if (custom.equals("crafting_artwork")) return new NativeCraftingArtwork(session, host.getItemFromBlockEntity());
            if (custom.equals("player_inventory")) return NativePageLayout.playerInventory();
            if (custom.equals("mode_button")) {
                boolean overclock = node.get("mode").getAsString().equals("overclock");
                return NativeMachineWidgets.toggle(WIDGETS, 89,
                    () -> session.state().getBoolean(overclock ? "overclock" : "cooling"),
                    () -> session.action(overclock ? 0 : 1, 0),
                    () -> Component.translatable("gui.neoecoae.crafting." + (overclock ? "overclock" : "active_cooling")
                        + "." + (session.state().getBoolean(overclock ? "overclock" : "cooling") ? "on" : "off")));
            }
            throw new IllegalArgumentException("Unknown source crafting node: " + custom);
        });
        panel.child(cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.left(true,
            cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(176, 0,
                () -> Component.translatable("gui.neoecoae.guide"), guide::openPanel),
            cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(
                () -> com.cleanroommc.modularui.drawable.UITexture.fullImage("neoecoae:textures/gui/widget/multiblock.png"),
                () -> Component.translatable("gui.neoecoae.multiblock.builder"), builder::openPanel),
            cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(64, 80,
                () -> Component.translatable("gui.neoecoae.computation.settings.substitutions"), () -> session.action(5, 0)),
            cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(16, 240,
                () -> Component.translatable("gui.neoecoae.host.network_frequency.cycle", session.state().getInt("frequency")),
                () -> session.action(4, 1))));
        panel.child(new com.cleanroommc.modularui.widget.ParentWidget<>().pos(251, 0).size(23, 30)
            .background(com.cleanroommc.modularui.drawable.UITexture.fullImage(
                "neoecoae:textures/gui/common/side_bar/gray_mirrored_button_slot_up.png"))
            .child(
                    new com.cleanroommc.modularui.widget.Widget<>().pos(0, 3)
                        .size(21, 21)
                        .background(com.cleanroommc.modularui.drawable.UITexture.fullImage("neoecoae:textures/gui/widget/information.png"))
                        .hoverBackground(com.cleanroommc.modularui.drawable.UITexture.fullImage("neoecoae:textures/gui/widget/information_highlight.png"))
            .tooltipDynamic(lines -> lines.add(Component.translatable(session.state().getBoolean("connected")
                ? "gui.neoecoae.host.network.connected" : "gui.neoecoae.host.network.disconnected").getString()))));
        return panel;
    }

    public static ModularPanel patternBus(ECOCraftingPatternBusBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        int rows = (host.getInternalInventory().size() + 8) / 9;
        var panel = ModularPanel.defaultPanel("neoecoae_crafting_pattern_bus", 170, 112 + rows * 18)
            .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.panel());
        panel.child(new NativeText(() -> Component.translatable("block.neoecoae.crafting_pattern_bus"), 1, 0x3f3d52)
            .pos(4, 6).size(162, 12));
        panel.child(new com.cleanroommc.modularui.widget.Widget<>().pos(3, 21).size(164, rows * 18 + 2)
            .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.inventoryBorder()));
        cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.inventoryFrames(panel, 4, 29 + rows * 18);
        var handler = new cn.dancingsnow.neoecoae.platform.ui.NativeItemHandler(host.getInternalInventory());
        for (int index = 0; index < host.getInternalInventory().size(); index++) panel.child(
            new PatternSlot().slot(new ModularSlot(handler, index))
                .background(cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.slot())
                .pos(4 + index % 9 * 18, 22 + index / 9 * 18));
        panel.child(SlotGroupWidget.playerInventory((index, slot) -> new NativeItemSlot().background(
            cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle.slot())).pos(4, 29 + rows * 18));
        return panel;
    }

    private static final class PatternSlot extends NativeItemSlot {
        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public void draw(com.cleanroommc.modularui.screen.viewport.ModularGuiContext context,
            com.cleanroommc.modularui.theme.WidgetThemeEntry<?> theme) {
            super.draw(context, theme);
            if (!getSlot().getHasStack()) NativePageLayout.texture(
                "neoecoae:textures/gui/vendor/ae2/states.png", 240, 128, 16, 16).draw(1, 1, 16, 16);
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

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.native_crafting." + key, arguments);
    }
}
