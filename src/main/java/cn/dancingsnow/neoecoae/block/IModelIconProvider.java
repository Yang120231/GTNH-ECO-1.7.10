package cn.dancingsnow.neoecoae.block;

import java.util.Map;

import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface IModelIconProvider {

    @SideOnly(Side.CLIENT)
    Map<String, IIcon> getModelIcons();
}
