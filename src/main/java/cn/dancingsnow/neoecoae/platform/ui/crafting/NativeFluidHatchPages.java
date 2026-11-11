package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.util.Collections;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.compat.nei.NEEGhostSlots;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.fluid.FluidTank;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.ui.NativeFluidSlot;
import cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar;
import cn.dancingsnow.neoecoae.platform.ui.NativeItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.NativeOwnedFluidSync;
import cn.dancingsnow.neoecoae.platform.ui.NativeSlotButton;
import cn.dancingsnow.neoecoae.platform.ui.NativeSlotGroups;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cn.dancingsnow.neoecoae.platform.ui.NativeUiStyle;
import cn.dancingsnow.neoecoae.platform.ui.NativeUpgradeSlot;
import cn.dancingsnow.neoecoae.platform.ui.storage.NativeStorageFilters;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Direct expansion of fluid_hatch.json: padding 4, gap 2 and the slot's two 10px margins. */
public final class NativeFluidHatchPages {

    private NativeFluidHatchPages() {}

    public static ModularPanel create(ECOFluidInputHatchBlockEntity host, EntityPlayer player, PanelSyncManager sync) {
        NativeFluidHatchSession session = new NativeFluidHatchSession(host);
        sync.syncValue("fluid.input.stock", session);
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_input_hatch", 176, 190)
            .background(NativeUiStyle.panel());
        panel.child(
            new NativeText(() -> Component.translatable("block.neoecoae.input_hatch"), 1, 0x3f3d52).pos(8, 6)
                .size(160, 10));
        panel.child(
            new NativeText(() -> text("selected"), 1, 0x3f3d52).pos(8, 29)
                .size(36, 10));
        panel.child(
            new MarkedFluidSlot(session, player).setEnabledIf(widget -> session.cardInstalled())
                .pos(45, 25)
                .size(18));
        panel.child(
            new BufferedFluidSlot(session)
                .syncHandler(new NativeOwnedFluidSync(host, host.tank.nativeTank(), true, true))
                .background(NativeUiStyle.inventoryBorder())
                .alwaysShowFull(false)
                .pos(80, 25)
                .size(18));
        panel.child(
            new NativeText(() -> text("stored", Long.toString(session.stored())), 6f / 9, 0x3f3d52).pos(8, 49)
                .size(160, 10));
        panel.child(
            new NativeText(() -> text("target"), 1, 0x3f3d52).pos(8, 66)
                .size(64, 10));
        panel.child(
            new TextFieldWidget().setMaxLength(19)
                .setPattern(Pattern.compile("[0-9]*"))
                .setEnabledIf(widget -> session.cardInstalled())
                .value(new StringValue.Dynamic(() -> Long.toString(session.target()), session::setTarget))
                .pos(73, 63)
                .size(94, 14));
        panel.child(
            new NativeText(() -> text(session.cardInstalled() ? "active" : "install"), 6f / 9, 0x3f3d52).pos(8, 85)
                .size(160, 10));
        String group = NativeSlotGroups.register(sync, "fluid.input.upgrades", 1, NativeSlotGroups.UPGRADE_PRIORITY);
        var card = new NativeUpgradeSlot().slot(new ModularSlot(new InvWrapper(host.getUpgrades()), 0).slotGroup(group))
            .tooltipDynamic(tooltip -> tooltip.add(text("upgrade").getString()));
        panel.child(NativeHostSideBar.upgrades(176, Collections.singletonList(card)));
        NativeUiStyle.inventoryFrames(panel, 7, 104);
        panel.child(
            SlotGroupWidget.playerInventory((index, slot) -> new NativeItemSlot().background(NativeUiStyle.slot()))
                .pos(7, 104));
        return panel;
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.neoecoae.fluid_input_hatch." + key, arguments);
    }

    private static final class BufferedFluidSlot extends NativeFluidSlot {

        private final NativeFluidHatchSession session;

        private BufferedFluidSlot(NativeFluidHatchSession session) {
            this.session = session;
        }

        @Override
        @SideOnly(Side.CLIENT)
        protected void addToolTip(com.cleanroommc.modularui.screen.RichTooltip tooltip) {
            var fluid = getFluidStack();
            if (fluid != null) {
                tooltip.addFromFluid(fluid);
                addAdditionalFluidInfo(tooltip, fluid);
            }
            tooltip.add(text("stored", Long.toString(session.stored())).getString())
                .newLine();
            tooltip.addLine(com.cleanroommc.modularui.api.drawable.IKey.lang("modularui2.fluid.click_combined"));
        }
    }

    private static final class MarkedFluidSlot extends NativeSlotButton<MarkedFluidSlot>
        implements RecipeViewerGhostIngredientSlot<ItemStack> {

        private final NativeFluidHatchSession session;

        private MarkedFluidSlot(NativeFluidHatchSession session, EntityPlayer player) {
            this.session = session;
            background(NativeUiStyle.slot());
            onMousePressed(button -> {
                if (button == 1) {
                    session.select(null);
                    return true;
                }
                if (button != 0) return false;
                return selectSample(player.inventory.getItemStack());
            });
            tooltipDynamic(tooltip -> {
                var fluid = session.selected();
                if (fluid != null) tooltip.add(fluid.getDisplayName())
                    .newLine();
                tooltip.add(text("filter_hint").getString());
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @SideOnly(Side.CLIENT)
        private boolean selectSample(ItemStack stack) {
            var key = NativeStorageFilters.sample(stack, false);
            if (!(key instanceof AEFluidKey fluid)) return false;
            session.select(fluid);
            return true;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public boolean handleDragAndDrop(ItemStack dragged, int button) {
            if (!areAncestorsEnabled() || !NEEGhostSlots.allowsDrop(button) || !selectSample(dragged)) return false;
            NEEGhostSlots.acceptedDrop(dragged);
            return true;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            var fluid = session.selected();
            if (fluid != null) GuiDraw.drawFluidTexture(fluid.toStack(1), 1, 1, 16, 16, context.getCurrentDrawingZ());
        }
    }

    public static ModularPanel create(TileEntity host, FluidTank tank, boolean input) {
        var panel = ModularPanel.defaultPanel("neoecoae_" + (input ? "input" : "output") + "_hatch", 170, 136)
            .background(NativeUiStyle.panel());
        panel.child(
            new NativeText(
                () -> Component.translatable("block.neoecoae." + (input ? "input" : "output") + "_hatch"),
                1,
                0x3f3d52).pos(4, 4)
                    .size(162, 9));
        panel.child(
            new NativeFluidSlot().syncHandler(new NativeOwnedFluidSync(host, tank.nativeTank(), input, true))
                .background(NativeUiStyle.inventoryBorder())
                .alwaysShowFull(false)
                .pos(76, 25)
                .size(18));
        NativeUiStyle.inventoryFrames(panel, 4, 55);
        panel.child(
            SlotGroupWidget.playerInventory((index, slot) -> new NativeItemSlot().background(NativeUiStyle.slot()))
                .pos(4, 55));
        return panel;
    }
}
