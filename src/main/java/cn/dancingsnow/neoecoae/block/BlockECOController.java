package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.ECOControllerTier;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public class BlockECOController extends BlockDirectionalModernModel {

    private final ECOControllerSubsystem subsystem;
    private final ECOControllerTier tier;
    private final String formedModelName;
    private final String mirroredFormedModelName;

    public BlockECOController(String id, String modelName, String formedModelName, String[] textureNames,
        ECOControllerSubsystem subsystem, ECOControllerTier tier) {
        this(id, modelName, formedModelName, formedModelName + "_mirrored", textureNames, subsystem, tier);
    }

    public BlockECOController(String id, String modelName, String formedModelName, String mirroredFormedModelName,
        String[] textureNames, ECOControllerSubsystem subsystem, ECOControllerTier tier) {
        super(id, modelName, textureNames);
        this.subsystem = subsystem;
        this.tier = tier;
        this.formedModelName = formedModelName;
        this.mirroredFormedModelName = mirroredFormedModelName;
    }

    public BlockECOController(String id, String modelName, String formedModelName, String[] textureNames,
        ModelFacing inventoryModelFacing, ECOControllerSubsystem subsystem, ECOControllerTier tier) {
        this(
            id,
            modelName,
            formedModelName,
            formedModelName + "_mirrored",
            textureNames,
            inventoryModelFacing,
            subsystem,
            tier);
    }

    public BlockECOController(String id, String modelName, String formedModelName, String mirroredFormedModelName,
        String[] textureNames, ModelFacing inventoryModelFacing, ECOControllerSubsystem subsystem,
        ECOControllerTier tier) {
        super(id, modelName, textureNames, inventoryModelFacing);
        this.subsystem = subsystem;
        this.tier = tier;
        this.formedModelName = formedModelName;
        this.mirroredFormedModelName = mirroredFormedModelName;
    }

    public ECOControllerSubsystem getSubsystem() {
        return this.subsystem;
    }

    public ECOControllerTier getTier() {
        return this.tier;
    }

    public String getFormedModelName() {
        return this.formedModelName;
    }

    public String getMirroredFormedModelName() {
        return this.mirroredFormedModelName;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        TileECOController controller = new TileECOController(this.subsystem, this.tier);
        controller.setFacingMeta(metadata);
        return controller;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECOController) {
            ((TileECOController) tile).setFacingMeta(world.getBlockMetadata(x, y, z));
        }
    }
}
