package cn.dancingsnow.neoecoae.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import appeng.container.implementations.ContainerPriority;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.container.ContainerCraftingHatch;
import cn.dancingsnow.neoecoae.gui.container.ContainerCraftingPatternBus;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOComputationController;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOCraftingController;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageController;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageInterface;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOStorageRecoveryTerminal;
import cn.dancingsnow.neoecoae.gui.container.ContainerECOStructureTerminal;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileCraftingHatch;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class NEGuiHandler implements IGuiHandler {

    public static final NEGuiHandler INSTANCE = new NEGuiHandler();

    private NEGuiHandler() {}

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == NEGuiIds.ECO_STRUCTURE_TERMINAL) {
            return new ContainerECOStructureTerminal(player);
        }
        if (id == NEGuiIds.ECO_STORAGE_RECOVERY_TERMINAL) {
            return new ContainerECOStorageRecoveryTerminal(player);
        }
        TileECOInterface storageInterface = getStorageInterface(id, world, x, y, z);
        if (storageInterface != null) {
            return new ContainerECOStorageInterface(storageInterface);
        }
        TileCraftingPatternBus patternBus = getPatternBus(id, world, x, y, z);
        if (patternBus != null) {
            return new ContainerCraftingPatternBus(player.inventory, patternBus);
        }
        TileCraftingHatch hatch = getCraftingHatch(id, world, x, y, z);
        if (hatch != null) {
            return new ContainerCraftingHatch(player.inventory, hatch);
        }

        TileECOController controller = getController(id, world, x, y, z);
        if (controller == null) {
            return null;
        }
        if (id == NEGuiIds.ECO_STORAGE_CONTROLLER) {
            return new ContainerECOStorageController(player.inventory, controller);
        }
        if (id == NEGuiIds.ECO_STORAGE_PRIORITY) {
            return new ContainerPriority(player.inventory, controller);
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
        if (id == NEGuiIds.ECO_STRUCTURE_TERMINAL) {
            return NeoECOAE.proxy.createStructureTerminalGui(player);
        }
        if (id == NEGuiIds.ECO_STORAGE_RECOVERY_TERMINAL) {
            return NeoECOAE.proxy.createStorageRecoveryTerminalGui(player);
        }
        TileECOInterface storageInterface = getStorageInterface(id, world, x, y, z);
        if (storageInterface != null) {
            return NeoECOAE.proxy.createStorageInterfaceGui(storageInterface);
        }
        TileCraftingPatternBus patternBus = getPatternBus(id, world, x, y, z);
        if (patternBus != null) {
            return NeoECOAE.proxy.createCraftingPatternBusGui(player.inventory, patternBus);
        }
        TileCraftingHatch hatch = getCraftingHatch(id, world, x, y, z);
        if (hatch != null) {
            return NeoECOAE.proxy.createCraftingHatchGui(player.inventory, hatch);
        }

        TileECOController controller = getController(id, world, x, y, z);
        if (controller != null && id == NEGuiIds.ECO_STORAGE_PRIORITY) {
            return NeoECOAE.proxy.createStoragePriorityGui(player.inventory, controller);
        }
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
        if (id == NEGuiIds.ECO_STORAGE_CONTROLLER || id == NEGuiIds.ECO_STORAGE_PRIORITY) {
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

    private static TileCraftingPatternBus getPatternBus(int id, World world, int x, int y, int z) {
        if (id != NEGuiIds.CRAFTING_PATTERN_BUS || world == null) {
            return null;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileCraftingPatternBus ? (TileCraftingPatternBus) tile : null;
    }

    private static TileCraftingHatch getCraftingHatch(int id, World world, int x, int y, int z) {
        if (id != NEGuiIds.CRAFTING_HATCH || world == null) {
            return null;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileCraftingHatch ? (TileCraftingHatch) tile : null;
    }

    private static TileECOInterface getStorageInterface(int id, World world, int x, int y, int z) {
        if (id != NEGuiIds.ECO_STORAGE_INTERFACE || world == null) {
            return null;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileECOInterface)) {
            return null;
        }
        TileECOInterface storageInterface = (TileECOInterface) tile;
        return storageInterface.getSubsystem() == ECOControllerSubsystem.STORAGE ? storageInterface : null;
    }
}
