package cn.dancingsnow.neoecoae.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOComputationController;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOCraftingController;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageController;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class NEGuiHandler implements IGuiHandler {

    public static final NEGuiHandler INSTANCE = new NEGuiHandler();

    private NEGuiHandler() {}

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileECOController controller = getController(id, world, x, y, z);
        if (controller == null) {
            return null;
        }
        if (id == NEGuiIds.ECO_STORAGE_CONTROLLER) {
            return new ContainerECOStorageController(player.inventory, controller);
        }
        if (id == NEGuiIds.ECO_COMPUTATION_CONTROLLER) {
            return new ContainerECOComputationController(player.inventory, controller);
        }
        if (id == NEGuiIds.ECO_CRAFTING_CONTROLLER) {
            return new ContainerECOCraftingController(player.inventory, controller);
        }
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileECOController controller = getController(id, world, x, y, z);
        return controller == null ? null : NeoECOAE.proxy.createHostControllerGui(id, player.inventory, controller);
    }

    private static TileECOController getController(int id, World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileECOController)) {
            return null;
        }
        TileECOController controller = (TileECOController) tile;
        if (id == NEGuiIds.ECO_STORAGE_CONTROLLER) {
            return controller.getSubsystem() == ECOControllerSubsystem.STORAGE ? controller : null;
        }
        if (id == NEGuiIds.ECO_COMPUTATION_CONTROLLER) {
            return controller.getSubsystem() == ECOControllerSubsystem.COMPUTATION ? controller : null;
        }
        if (id == NEGuiIds.ECO_CRAFTING_CONTROLLER) {
            return controller.getSubsystem() == ECOControllerSubsystem.CRAFTING ? controller : null;
        }
        return null;
    }
}
