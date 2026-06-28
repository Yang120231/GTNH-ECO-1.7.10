package cn.dancingsnow.neoecoae.block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.client.render.model.ModernIconRegistrar;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockModelDrive extends Block {

    private static int renderId = -1;

    private final String emptyModelName;
    private final String fullModelName;
    private final String[] textureNames;
    private final String particleTextureName;

    @SideOnly(Side.CLIENT)
    private final Map<String, IIcon> modelIcons = new HashMap<String, IIcon>();

    @SideOnly(Side.CLIENT)
    private IIcon particleIcon;

    public BlockModelDrive(String id, String emptyModelName, String fullModelName, String[] textureNames,
        String particleTextureName) {
        super(Material.iron);
        this.emptyModelName = emptyModelName;
        this.fullModelName = fullModelName;
        this.textureNames = textureNames;
        this.particleTextureName = particleTextureName;
        this.setBlockName(id);
        this.setCreativeTab(NECreativeTabs.NEO_ECO_AE);
        this.setHardness(5.0F);
        this.setResistance(10.0F);
        this.setStepSound(Block.soundTypeMetal);
        this.setHarvestLevel("pickaxe", 2);
    }

    public static void setRenderId(int renderId) {
        BlockModelDrive.renderId = renderId;
    }

    public String getEmptyModelName() {
        return this.emptyModelName;
    }

    public String getFullModelName() {
        return this.fullModelName;
    }

    public String getParticleTextureName() {
        return this.particleTextureName;
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

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z, ModelFacingHelper.getFacingMetaFromYaw(placer.rotationYaw), 3);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        ModernIconRegistrar.registerIcons(register, this.textureNames, this.modelIcons);
        this.particleIcon = this.modelIcons.get(this.particleTextureName);
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
