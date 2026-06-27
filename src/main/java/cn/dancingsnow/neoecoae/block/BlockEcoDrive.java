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
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockEcoDrive extends Block {

    private static final String[] TEXTURES = { "neoecoae:block/storage/casing", "neoecoae:block/storage/casing_side",
        "neoecoae:block/storage/casing_back", "neoecoae:block/storage/drive/drive_north",
        "neoecoae:block/storage/drive/drive_north_on", "neoecoae:block/storage/drive/drive_inside",
        "neoecoae:block/storage/drive/drive_inside_top_bottom" };

    private static int renderId = -1;

    @SideOnly(Side.CLIENT)
    private final Map<String, IIcon> modelIcons = new HashMap<String, IIcon>();

    @SideOnly(Side.CLIENT)
    private IIcon particleIcon;

    public BlockEcoDrive() {
        super(Material.iron);
        this.setBlockName("eco_drive");
        this.setCreativeTab(NECreativeTabs.NEO_ECO_AE);
        this.setHardness(5.0F);
        this.setResistance(10.0F);
        this.setStepSound(Block.soundTypeMetal);
        this.setHarvestLevel("pickaxe", 2);
    }

    public static void setRenderId(int renderId) {
        BlockEcoDrive.renderId = renderId;
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
        world.setBlockMetadataWithNotify(x, y, z, getFacingMetaFromYaw(placer.rotationYaw), 3);
    }

    public static int getFacingMetaFromYaw(float yaw) {
        int quadrant = MathHelper.floor_double(yaw * 4.0F / 360.0F + 0.5D) & 3;
        switch (quadrant) {
            case 0:
                return 0; // north
            case 1:
                return 1; // east
            case 2:
                return 2; // south
            case 3:
                return 3; // west
            default:
                return 0;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        this.modelIcons.clear();
        for (String texture : TEXTURES) {
            String legacyName = toLegacyIconName(texture);
            this.modelIcons.put(texture, register.registerIcon(legacyName));
        }
        this.particleIcon = this.modelIcons.get("neoecoae:block/storage/casing_side");
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
