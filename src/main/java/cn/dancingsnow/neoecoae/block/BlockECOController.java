package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.NEGuiIds;
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

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileECOController)) {
            return false;
        }
        if (!world.isRemote) {
            player.openGui(NeoECOAE.instance, this.guiId(), world, x, y, z);
        }
        return true;
    }

    private int guiId() {
        if (this.subsystem == ECOControllerSubsystem.COMPUTATION) {
            return NEGuiIds.ECO_COMPUTATION_CONTROLLER;
        }
        if (this.subsystem == ECOControllerSubsystem.CRAFTING) {
            return NEGuiIds.ECO_CRAFTING_CONTROLLER;
        }
        return NEGuiIds.ECO_STORAGE_CONTROLLER;
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECOController && ((TileECOController) tile).blocksWorldRemoval()) {
            if (!world.isRemote && player != null) {
                player.addChatMessage(new ChatComponentTranslation("chat.neoecoae.storage.infinite_remove_blocked"));
            }
            return false;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECOController && ((TileECOController) tile).blocksWorldRemoval()) {
            return;
        }
        super.onBlockExploded(world, x, y, z, explosion);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECOController controller) {
            if (controller.blocksWorldRemoval()) {
                return;
            }
            ItemStack stack = controller.getStackInSlot(0);
            if (stack != null && !world.isRemote) {
                world.spawnEntityInWorld(new EntityItem(world, x + 0.5D, y + 0.5D, z + 0.5D, stack.copy()));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
