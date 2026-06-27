package cn.dancingsnow.neoecoae.block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockModernModel extends Block {

    private static int renderId = -1;

    private final String modelName;
    private final String[] textureNames;

    @SideOnly(Side.CLIENT)
    private final Map<String, IIcon> modelIcons = new HashMap<String, IIcon>();

    @SideOnly(Side.CLIENT)
    private IIcon particleIcon;

    public BlockModernModel(String id, String modelName, String[] textureNames) {
        super(Material.iron);
        this.modelName = modelName;
        this.textureNames = textureNames;
        this.setBlockName(id);
        this.setCreativeTab(NECreativeTabs.NEO_ECO_AE);
        this.setHardness(5.0F);
        this.setResistance(10.0F);
        this.setStepSound(Block.soundTypeMetal);
        this.setHarvestLevel("pickaxe", 2);
    }

    public static void setRenderId(int renderId) {
        BlockModernModel.renderId = renderId;
    }

    public String getModelName() {
        return this.modelName;
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        this.modelIcons.clear();
        for (String texture : this.textureNames) {
            this.modelIcons.put(texture, register.registerIcon(toLegacyIconName(texture)));
        }
        this.particleIcon = this.modelIcons.get(this.textureNames[0]);
        this.blockIcon = this.particleIcon;
    }

    @SideOnly(Side.CLIENT)
    public Map<String, IIcon> getModelIcons() {
        return Collections.unmodifiableMap(this.modelIcons);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        return this.particleIcon != null ? this.particleIcon : this.blockIcon;
    }

    private static String toLegacyIconName(String modernTexture) {
        String prefix = NeoECOAE.MODID + ":block/";
        if (modernTexture.startsWith(prefix)) {
            return NeoECOAE.MODID + ":" + modernTexture.substring(prefix.length());
        }
        return modernTexture.replace(":block/", ":");
    }
}
