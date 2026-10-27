package cn.dancingsnow.neoecoae.platform.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.guidenh.guide.GuidesCommon;
import com.hfstudio.guidenh.guide.PageAnchor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Loaded only on clients with GuideNH; the guide itself is discovered from packaged resources. */
@SideOnly(Side.CLIENT)
final class NativeGuideNh {

    private NativeGuideNh() {}

    static void open(String page, EntityPlayer player) {
        GuidesCommon.openGuide(
            player,
            new ResourceLocation("neoecoae", "guidenh"),
            PageAnchor.page(new ResourceLocation("neoecoae", "neoecoae_intro/" + page + ".md")));
    }
}
