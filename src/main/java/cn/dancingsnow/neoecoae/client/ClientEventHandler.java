package cn.dancingsnow.neoecoae.client;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.world.WorldEvent;

import cn.dancingsnow.neoecoae.all.NEStorageItems;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class ClientEventHandler {

    public static final ClientEventHandler INSTANCE = new ClientEventHandler();

    private ClientEventHandler() {}

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) {
            NEStorageItems.registerComputationCellModelIcons(event.map);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world != null && event.world.isRemote) {
            ECOFormationVisibility.clearClient();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        ClientPatternHighlight.render(event);
    }
}
