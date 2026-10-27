package cn.dancingsnow.neoecoae.platform.ui.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.google.gson.JsonObject;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.StorageInterfaceFilter;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The complete source storage-interface page, rendered by native MUI2 widgets. */
public final class NativeStorageInterfacePages {

    private static final String ATLAS = "neoecoae:textures/guis/storage/storage_widget.png";
    private final ECOMachineInterfaceBlockEntity<?> host;
    private final EntityPlayer player;
    private final NativeStorageInterfaceSession session;

    private NativeStorageInterfacePages(ECOMachineInterfaceBlockEntity<?> host, EntityPlayer player,
        PanelSyncManager sync) {
        this.host = host;
        this.player = player;
        session = new NativeStorageInterfaceSession(host);
        sync.syncValue("storage.interface", session);
    }

    public static ModularPanel create(ECOMachineInterfaceBlockEntity<?> host, EntityPlayer player,
        PanelSyncManager sync) {
        return new NativeStorageInterfacePages(host, player, sync).build();
    }

    private ModularPanel build() {
        NativePageLayout layout = new NativePageLayout("storage/storage_interface");
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_storage_interface", layout.width(), layout.height());
        for (var value : layout.root()
            .getAsJsonArray("children")) {
            JsonObject node = value.getAsJsonObject();
            Widget<?> widget;
            if ("texture".equals(
                node.get("type")
                    .getAsString())) {
                var sprite = node.getAsJsonArray("sprite");
                widget = new Widget<>().background(
                    NativePageLayout.texture(
                        node.get("texture")
                            .getAsString(),
                        sprite.get(0)
                            .getAsInt(),
                        sprite.get(1)
                            .getAsInt(),
                        sprite.get(2)
                            .getAsInt(),
                        sprite.get(3)
                            .getAsInt()));
            } else {
                String custom = node.get("custom")
                    .getAsString();
                if (custom.equals("toolbar")) {
                    panel.child(toolbar());
                    continue;
                }
                switch (custom) {
                    case "player_inventory":
                        widget = NativePageLayout.playerInventory();
                        break;
                    case "resource_filters":
                        widget = filters();
                        break;
                    case "mode_button":
                        widget = new ModeButton(
                            ECOStorageInterfaceMode.valueOf(
                                node.get("mode")
                                    .getAsString()));
                        break;
                    default:
                        widget = new NativeText(() -> label(custom), 6f / 9, 0x3f3d52);
                        break;
                }
            }
            layout.place(widget, node);
            panel.child(widget);
        }
        return panel;
    }

    private Component label(String kind) {
        switch (kind) {
            case "title":
                return Component.literal(
                    host.getItemFromBlockEntity()
                        .getDisplayName());
            case "mode_caption":
                return modeName(session.mode());
            case "structure_status":
                return Component.translatable("gui.neoecoae.storage_interface.structure")
                    .append(": ")
                    .append(
                        Component.translatable(
                            "gui.neoecoae.storage_interface.infinite_"
                                + (session.infiniteReady() ? "ready" : "unavailable")));
            case "network_status":
                return Component.translatable("gui.neoecoae.storage_interface.network")
                    .append(": ")
                    .append(
                        Component.translatable(
                            "gui.neoecoae.storage_interface." + (session.online() ? "connected" : "disconnected"))
                            .withColor(session.online() ? 0x258345 : 0xb53643));
            case "transfer_status":
                return Component.translatable(
                    "gui.neoecoae.storage_interface.transfer",
                    HostText.ae2Amount(session.transferred()));
            case "filter_caption":
                return Component.translatable("gui.neoecoae.storage_interface.filter.title");
            case "filter_hint":
                return Component.translatable("gui.neoecoae.storage_interface.filter.hint");
            default:
                throw new IllegalArgumentException("Unbound source storage interface node: " + kind);
        }
    }

    private ParentWidget<?> toolbar() {
        return cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.left(false,
            settingButton(true), settingButton(false));
    }

    private ButtonWidget<?> settingButton(boolean creative) {
        return cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar.button(
            () -> UITexture.fullImage("neoecoae:textures/gui/widget/"
                + (creative ? "ignore_creative_" : "exclude_storage_bus_")
                + ((creative ? session.ignoringCreative() : session.excludingStorageBus()) ? "on" : "off") + ".png"),
            () -> Component.translatable("gui.neoecoae.storage_interface."
                + (creative ? "ignore_creative" : "exclude_storage_bus")
                + ((creative ? session.ignoringCreative() : session.excludingStorageBus()) ? ".on" : ".off")),
            creative ? session::toggleCreative : session::toggleStorageBus);
    }

    private ParentWidget<?> filters() {
        ParentWidget<?> grid = new ParentWidget<>();
        for (int index = 0; index < StorageInterfaceFilter.SLOTS; index++) grid.child(
            new FilterButton(index).pos(index % 9 * 18, index / 9 * 18)
                .size(18));
        return grid;
    }

    private static Component modeName(ECOStorageInterfaceMode mode) {
        return Component.translatable("gui.neoecoae.storage_interface.mode." + mode.getSerializedName());
    }

    private final class ModeButton extends cn.dancingsnow.neoecoae.platform.ui.NativeButtonWidget<ModeButton> {

        private final ECOStorageInterfaceMode mode;

        ModeButton(ECOStorageInterfaceMode mode) {
            this.mode = mode;
            onMousePressed(button -> {
                if (button != 0) return false;
                session.selectMode(mode);
                return true;
            });
            tooltipDynamic(tooltip -> tooltip.add(modeName(mode).getString()));
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            boolean selected = isPressed() || session.mode() == mode;
            int height = selected ? 17 : 18;
            NativePageLayout
                .texture(ATLAS, (mode.ordinal() + (isHovering() || isPressed() ? 3 : 0)) * 16, selected ? 159 : 140, 16, height)
                .draw(0, 18 - height, 16, height);
        }
    }

    private final class FilterButton extends cn.dancingsnow.neoecoae.platform.ui.NativeSlotButton<FilterButton>
        implements RecipeViewerGhostIngredientSlot<ItemStack> {

        private final int index;

        FilterButton(int index) {
            this.index = index;
            onMousePressed(button -> {
                if (button == 1) {
                    session.setFilter(index, null);
                    return true;
                }
                ItemStack carried = player.inventory.getItemStack();
                if (button != 0 || ItemStacks.isEmpty(carried)) return false;
                session.setFilter(index, NativeStorageFilters.sample(carried, Interactable.hasShiftDown()));
                return true;
            });
            tooltipDynamic(tooltip -> {
                var key = session.filter(index);
                if (key != null) tooltip.add(key.getDisplayName())
                    .newLine();
                if (session.unavailable(index)) tooltip
                    .add(
                        Component.translatable("gui.neoecoae.storage_interface.filter.unavailable")
                            .getString())
                    .newLine();
                tooltip.add(
                    Component.translatable("gui.neoecoae.storage_interface.filter.edit")
                        .getString())
                    .newLine();
                tooltip.add(
                    Component.translatable("gui.neoecoae.storage_interface.filter.rule")
                        .getString());
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public boolean handleDragAndDrop(ItemStack dragged, int button) {
            if (!areAncestorsEnabled() || ItemStacks.isEmpty(dragged)) return false;
            session.setFilter(index, NativeStorageFilters.sample(dragged, Interactable.hasShiftDown()));
            return true;
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
            var key = session.filter(index);
            if (key instanceof AEItemKey item)
                GuiDraw.drawItem(item.toStack(1), 1, 1, 16, 16, context.getCurrentDrawingZ());
            else if (key instanceof AEFluidKey fluid)
                GuiDraw.drawFluidTexture(fluid.toStack(1), 1, 1, 16, 16, context.getCurrentDrawingZ());
            else NativePageLayout.texture(ATLAS, 240, 0, 16, 16)
                .draw(1, 1, 16, 16);
        }
    }
}
