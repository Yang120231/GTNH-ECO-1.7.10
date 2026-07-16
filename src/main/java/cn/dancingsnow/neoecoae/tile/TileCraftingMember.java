package cn.dancingsnow.neoecoae.tile;

import net.minecraft.tileentity.TileEntity;

public abstract class TileCraftingMember extends TileEntity {

    private TileECOController cachedController;

    protected TileECOController findCraftingController() {
        if (this.worldObj == null) {
            return null;
        }
        if (this.cachedController != null && this.cachedController.getWorldObj() == this.worldObj
            && this.cachedController.getSubsystem() == ECOControllerSubsystem.CRAFTING
            && this.cachedController.isFormed()
            && this.cachedController.hasCraftingMemberBlock(this.xCoord, this.yCoord, this.zCoord)) {
            return this.cachedController;
        }
        for (TileECOController controller : ECOControllerRegistry.controllers(this.worldObj)) {
            if (controller.getSubsystem() == ECOControllerSubsystem.CRAFTING && controller.isFormed()
                && controller.hasCraftingMemberBlock(this.xCoord, this.yCoord, this.zCoord)) {
                this.cachedController = controller;
                return controller;
            }
        }
        this.cachedController = null;
        return null;
    }

    protected void notifyCraftingControllerChanged() {
        TileECOController controller = this.findCraftingController();
        if (controller != null) {
            controller.onCraftingMemberStateChanged();
        }
    }

    protected void notifyCraftingPatternsChanged() {
        TileECOController controller = this.findCraftingController();
        if (controller != null) {
            controller.onCraftingPatternsChanged();
        }
    }
}
