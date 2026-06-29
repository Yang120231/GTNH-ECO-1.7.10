package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class StoragePriorityTabs {

    private static final int AE2_PRIORITY_ICON = 2 + 4 * 16;

    private StoragePriorityTabs() {}

    static GuiTabButton priorityButton(int guiLeft, int guiTop, RenderItem itemRender) {
        return new GuiTabButton(
            guiLeft + StorageControllerLayout.PRIORITY_TAB_X,
            guiTop + StorageControllerLayout.PRIORITY_TAB_Y,
            AE2_PRIORITY_ICON,
            GuiText.Priority.getLocal(),
            itemRender);
    }

    static GuiTabButton storageButton(TileECOController controller, int guiLeft, int guiTop, int xSize,
        RenderItem itemRender) {
        GuiTabButton button = new GuiTabButton(
            guiLeft + xSize - StorageControllerLayout.TAB_SIZE,
            guiTop,
            hostIcon(controller),
            hostTitle(controller),
            itemRender);
        button.setHideEdge(13);
        return button;
    }

    private static String hostTitle(TileECOController controller) {
        return controller == null || controller.getBlockType() == null ? "ECO Storage Host"
            : controller.getBlockType()
                .getLocalizedName();
    }

    private static ItemStack hostIcon(TileECOController controller) {
        ECOControllerTier tier = controller == null ? ECOControllerTier.L4 : controller.getTier();
        if (tier == ECOControllerTier.L9) {
            return new ItemStack(NEBlocks.storageSystemL9);
        }
        if (tier == ECOControllerTier.L6) {
            return new ItemStack(NEBlocks.storageSystemL6);
        }
        return new ItemStack(NEBlocks.storageSystemL4);
    }
}
