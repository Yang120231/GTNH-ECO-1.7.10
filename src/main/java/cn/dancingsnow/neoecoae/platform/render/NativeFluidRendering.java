package cn.dancingsnow.neoecoae.platform.render;

import net.minecraftforge.client.event.TextureStitchEvent;

import cn.dancingsnow.neoecoae.platform.recipe.SourceFluids;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class NativeFluidRendering {

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) return;
        var fluid = SourceFluids.ownCryotheum();
        if (fluid == null) return;
        fluid.setIcons(
            event.map.registerIcon("neoecoae:cryotheum_solution_still"),
            event.map.registerIcon("neoecoae:cryotheum_solution_flow"));
    }
}
