package cn.dancingsnow.neoecoae;

import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.block.BlockEcoDrive;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.client.render.EcoDriveItemRenderer;
import cn.dancingsnow.neoecoae.client.render.EcoDriveModels;
import cn.dancingsnow.neoecoae.client.render.EcoDriveRenderHandler;
import cn.dancingsnow.neoecoae.client.render.ModernBlockItemRenderer;
import cn.dancingsnow.neoecoae.client.render.ModernBlockModels;
import cn.dancingsnow.neoecoae.client.render.ModernBlockRenderHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        registerRenderers();
    }

    private void registerRenderers() {
        int ecoDriveRenderId = RenderingRegistry.getNextAvailableRenderId();
        BlockEcoDrive.setRenderId(ecoDriveRenderId);
        EcoDriveModels.load();
        RenderingRegistry.registerBlockHandler(new EcoDriveRenderHandler(ecoDriveRenderId));
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(NEBlocks.ecoDrive), new EcoDriveItemRenderer());

        int modernBlockRenderId = RenderingRegistry.getNextAvailableRenderId();
        BlockModernModel.setRenderId(modernBlockRenderId);
        ModernBlockModels.load("aluminum_alloy_casing");
        ModernBlockModels.load("black_tungsten_alloy_casing");
        RenderingRegistry.registerBlockHandler(new ModernBlockRenderHandler(modernBlockRenderId));
        MinecraftForgeClient.registerItemRenderer(
            Item.getItemFromBlock(NEBlocks.aluminumAlloyCasing),
            new ModernBlockItemRenderer(NEBlocks.aluminumAlloyCasing));
        MinecraftForgeClient.registerItemRenderer(
            Item.getItemFromBlock(NEBlocks.blackTungstenAlloyCasing),
            new ModernBlockItemRenderer(NEBlocks.blackTungstenAlloyCasing));
    }
}
