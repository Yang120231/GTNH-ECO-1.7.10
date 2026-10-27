package cn.dancingsnow.neoecoae.platform.ui.computation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiDraw;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class NativeComputationInterfacePages {

    private NativeComputationInterfacePages() {}

    public static ModularPanel create(ECOMachineInterfaceBlockEntity<?> host, EntityPlayer player,
        PanelSyncManager sync) {
        var session = new NativeFuzzyPlanningSession(host);
        sync.syncValue("computation.filters", session);
        var layout = new NativePageLayout("computation_interface");
        var panel = cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel
            .create("neoecoae_computation_interface", layout.width(), layout.height())
            .background(NativePageLayout.texture("neoecoae:textures/guis/nbtbench.png", 0, 0, 176, 253));
        layout.children(panel, node -> {
            switch (node.get("name")
                .getAsString()) {
                case "computation_interface_title":
                    return new NativeText(
                        () -> Component.translatable("block.neoecoae.computation_interface"),
                        1,
                        0x3f3d52);
                case "computation_interface_hint":
                    return new NativeText(
                        () -> Component.translatable("gui.neoecoae.computation_interface.hint"),
                        1,
                        0x6d6a82);
                case "computation_fuzzy_grid":
                    var grid = new ParentWidget<>().size(162, 126)
                        .background(IDrawable.EMPTY);
                    for (int index = 0; index < 63; index++) grid.child(
                        new Filter(session, player, index).name("fuzzy-filter-" + index)
                            .pos(index % 9 * 18, index / 9 * 18)
                            .size(18));
                    return grid;
                default:
                    throw new IllegalArgumentException("Unknown computation interface node " + node);
            }
        });
        for (var child : panel.getChildren()) if ("computation-interface-player-inventory".equals(child.getName())) {
            for (var slot : child.getChildren()) {
                int index = Integer.parseInt(
                    slot.getName()
                        .substring("slot_".length()));
                if (index >= 9) ((com.cleanroommc.modularui.widget.Widget<?>) slot)
                    .pos((index - 9) % 9 * 18, (index - 9) / 9 * 18 + 1);
            }
        }
        return panel;
    }

    private static final class Filter extends cn.dancingsnow.neoecoae.platform.ui.NativeSlotButton<Filter>
        implements RecipeViewerGhostIngredientSlot<ItemStack> {

        private final NativeFuzzyPlanningSession session;
        private final int index;

        Filter(NativeFuzzyPlanningSession session, EntityPlayer player, int index) {
            this.session = session;
            this.index = index;
            background(IDrawable.EMPTY);
            hoverBackground(IDrawable.EMPTY);
            onMousePressed(button -> {
                if (button == 1) session.setSample(index, null);
                else if (button == 0 && !ItemStacks.isEmpty(player.inventory.getItemStack()))
                    session.setSample(index, player.inventory.getItemStack());
                else return false;
                return true;
            });
            tooltipDynamic(lines -> {
                var sample = session.sample(index);
                if (!ItemStacks.isEmpty(sample)) lines.addFromItem(sample);
                lines.add(
                    Component.translatable("gui.neoecoae.computation_interface.hint")
                        .getString());
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public boolean handleDragAndDrop(ItemStack sample, int button) {
            if (!areAncestorsEnabled() || ItemStacks.isEmpty(sample)) return false;
            session.setSample(index, sample);
            return true;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            NativeUiDraw.item(session.sample(index), 1, 1, context.getCurrentDrawingZ());
        }
    }
}
