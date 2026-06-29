package cn.dancingsnow.neoecoae;

import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEStorageItems;
import cn.dancingsnow.neoecoae.block.BlockECOController;
import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.client.ClientEventHandler;
import cn.dancingsnow.neoecoae.client.gui.GuiECOComputationController;
import cn.dancingsnow.neoecoae.client.gui.GuiECOCraftingController;
import cn.dancingsnow.neoecoae.client.gui.GuiECOStorageController;
import cn.dancingsnow.neoecoae.client.gui.GuiECOStoragePriority;
import cn.dancingsnow.neoecoae.client.render.ComputationCellItemModels;
import cn.dancingsnow.neoecoae.client.render.ComputationCellItemRenderer;
import cn.dancingsnow.neoecoae.client.render.DriveModels;
import cn.dancingsnow.neoecoae.client.render.DriveRenderHandler;
import cn.dancingsnow.neoecoae.client.render.ModernBlockModels;
import cn.dancingsnow.neoecoae.client.render.ModernBlockRenderHandler;
import cn.dancingsnow.neoecoae.gui.NEGuiIds;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.INSTANCE);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        registerRenderers();
    }

    @Override
    public Object createHostControllerGui(int id, net.minecraft.entity.player.InventoryPlayer playerInventory,
        TileECOController controller) {
        if (id == NEGuiIds.ECO_COMPUTATION_CONTROLLER) {
            return new GuiECOComputationController(playerInventory, controller);
        }
        if (id == NEGuiIds.ECO_CRAFTING_CONTROLLER) {
            return new GuiECOCraftingController(playerInventory, controller);
        }
        return new GuiECOStorageController(playerInventory, controller);
    }

    @Override
    public Object createStoragePriorityGui(net.minecraft.entity.player.InventoryPlayer playerInventory,
        TileECOController controller) {
        return new GuiECOStoragePriority(playerInventory, controller);
    }

    private void registerRenderers() {
        int driveRenderId = RenderingRegistry.getNextAvailableRenderId();
        BlockModelDrive.setRenderId(driveRenderId);
        DriveModels.load((BlockModelDrive) NEBlocks.ecoDrive);
        DriveModels.load((BlockModelDrive) NEBlocks.computationDrive);
        RenderingRegistry.registerBlockHandler(new DriveRenderHandler(driveRenderId));

        int modernBlockRenderId = RenderingRegistry.getNextAvailableRenderId();
        BlockModernModel.setRenderId(modernBlockRenderId);
        for (BlockModernModel block : NEBlocks.getModernModelBlocks()) {
            ModernBlockModels.load(block.getModelName());
            if (block instanceof BlockECOController) {
                BlockECOController controller = (BlockECOController) block;
                ModernBlockModels.load(controller.getFormedModelName());
                ModernBlockModels.load(controller.getMirroredFormedModelName());
            } else {
                String formedModelName = block.getFormedModelName();
                if (formedModelName != null) {
                    ModernBlockModels.load(formedModelName);
                }
                String mirroredFormedModelName = block.getMirroredFormedModelName();
                if (mirroredFormedModelName != null) {
                    ModernBlockModels.load(mirroredFormedModelName);
                }
                for (String additionalFormedModelName : block.getAdditionalFormedModelNames()) {
                    ModernBlockModels.load(additionalFormedModelName);
                }
            }
        }
        RenderingRegistry.registerBlockHandler(new ModernBlockRenderHandler(modernBlockRenderId));

        ComputationCellItemModels.preload();
        ComputationCellItemRenderer computationCellRenderer = new ComputationCellItemRenderer();
        MinecraftForgeClient.registerItemRenderer(NEStorageItems.ecoComputationCellL4, computationCellRenderer);
        MinecraftForgeClient.registerItemRenderer(NEStorageItems.ecoComputationCellL6, computationCellRenderer);
        MinecraftForgeClient.registerItemRenderer(NEStorageItems.ecoComputationCellL9, computationCellRenderer);
    }
}
