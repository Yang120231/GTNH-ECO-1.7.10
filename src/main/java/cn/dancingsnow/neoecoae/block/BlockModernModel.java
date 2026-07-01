package cn.dancingsnow.neoecoae.block;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.client.render.model.ModernIconRegistrar;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockModernModel extends Block implements IModelIconProvider {

    private static int renderId = -1;

    private final String modelName;
    private final String[] textureNames;
    private final ModelFacing inventoryModelFacing;

    private final Map<String, IIcon> modelIcons = new HashMap<>();

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
        this.setLightOpacity(255);
        this.useNeighborBrightness = true;
    }

    public static void setRenderId(int renderId) {
        BlockModernModel.renderId = renderId;
    }

    public String getModelName() {
        return this.modelName;
    }

    public String getFormedModelName() {
        return null;
    }

    public String getMirroredFormedModelName() {
        return null;
    }

    public String[] getAdditionalFormedModelNames() {
        return new String[0];
    }

    public ModelFacing getModelFacing(int meta) {
        return ModelFacing.NORTH;
    }

    public ModelFacing getFormedModelFacing(int meta, boolean mirrored) {
        return this.getModelFacing(meta);
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

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        ModelLightingHelper.updateNeighborLighting(world, x, y, z);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        super.breakBlock(world, x, y, z, block, meta);
        ModelLightingHelper.updateNeighborLighting(world, x, y, z);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!ECOStorageStructureRemovalGuard.canRemoveOrNotify(world, player, x, y, z)) {
            return false;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
        if (!ECOStorageStructureRemovalGuard.canRemove(world, x, y, z)) {
            return;
        }
        super.onBlockExploded(world, x, y, z, explosion);
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
