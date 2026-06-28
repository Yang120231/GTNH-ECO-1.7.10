package cn.dancingsnow.neoecoae.block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.client.render.model.ModernIconRegistrar;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockModernModel extends Block {

    private static int renderId = -1;

    private final String modelName;
    private final String[] textureNames;
    private final ModelFacing inventoryModelFacing;

    @SideOnly(Side.CLIENT)
    private final Map<String, IIcon> modelIcons = new HashMap<String, IIcon>();

    @SideOnly(Side.CLIENT)
    private IIcon particleIcon;

    public BlockModernModel(String id, String modelName, String[] textureNames) {
        this(id, modelName, textureNames, ModelFacing.NORTH);
    }

    public BlockModernModel(String id, String modelName, String[] textureNames, ModelFacing inventoryModelFacing) {
        super(Material.iron);
        this.modelName = modelName;
        this.textureNames = textureNames;
        this.inventoryModelFacing = inventoryModelFacing;
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

    public ModelFacing getModelFacing(int meta) {
        return ModelFacing.NORTH;
    }

    public ModelFacing getInventoryModelFacing() {
        return this.inventoryModelFacing;
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
        ModernIconRegistrar.registerIcons(register, this.textureNames, this.modelIcons);
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
}
