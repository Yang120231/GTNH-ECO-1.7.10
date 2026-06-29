package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.gui.implementations.GuiPriority;
import appeng.client.gui.widgets.GuiTabButton;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.PacketStorageHostAction;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiECOStoragePriority extends GuiPriority {

    private final TileECOController controller;
    private GuiTabButton storageButton;

    public GuiECOStoragePriority(InventoryPlayer playerInventory, TileECOController controller) {
        super(playerInventory, controller);
        this.controller = controller;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.storageButton = StoragePriorityTabs.storageButton(this.controller, this.guiLeft, this.guiTop, this.xSize,
            this.itemRender);
        this.buttonList.add(this.storageButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == this.storageButton) {
            NENetwork.CHANNEL.sendToServer(
                new PacketStorageHostAction(this.controller, PacketStorageHostAction.Action.OPEN_STORAGE));
            return;
        }
        super.actionPerformed(button);
    }
}
