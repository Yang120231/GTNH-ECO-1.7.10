package cn.dancingsnow.neoecoae.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;

import cn.dancingsnow.neoecoae.block.BlockComputationTransmitter;
import cn.dancingsnow.neoecoae.block.BlockECOController;
import cn.dancingsnow.neoecoae.block.BlockModernModel;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationVisibility;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public class ModernBlockRenderHandler implements ISimpleBlockRenderingHandler {

    private final int renderId;

    public ModernBlockRenderHandler(int renderId) {
        this.renderId = renderId;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        if (block instanceof BlockModernModel) {
            BlockModernModel modelBlock = (BlockModernModel) block;
            EcoModelRenderer.renderInventoryBlock(
                ModernBlockModels.get(modelBlock.getModelName()),
                modelBlock.getInventoryModelFacing(),
                modelBlock.getModelIcons());
        }
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        if (!(block instanceof BlockModernModel)) {
            return false;
        }
        if (ECOFormationVisibility.isHidden(world, x, y, z)) {
            return true;
        }

        BlockModernModel modelBlock = (BlockModernModel) block;
        boolean formedMember = ECOFormationVisibility.shouldRenderFormedMember(world, x, y, z);
        boolean mirroredFormedMember = ECOFormationVisibility.isMirroredFormedMember(world, x, y, z);
        EcoModelRenderer.renderWorld(
            ModernBlockModels.get(getWorldModelName(modelBlock, world, x, y, z)),
            formedMember ? modelBlock.getFormedModelFacing(world.getBlockMetadata(x, y, z), mirroredFormedMember)
                : modelBlock.getModelFacing(world.getBlockMetadata(x, y, z)),
            modelBlock.getModelIcons(),
            world,
            x,
            y,
            z,
            block,
            renderer);
        return true;
    }

    private static String getWorldModelName(BlockModernModel block, IBlockAccess world, int x, int y, int z) {
        if (ECOFormationVisibility.shouldRenderFormedMember(world, x, y, z)) {
            String mirroredFormedModelName = ECOFormationVisibility.isMirroredFormedMember(world, x, y, z)
                ? block.getMirroredFormedModelName()
                : null;
            if (mirroredFormedModelName != null) {
                return mirroredFormedModelName;
            }

            if (block instanceof BlockComputationTransmitter) {
                return ((BlockComputationTransmitter) block)
                    .getFormedModelName(ECOFormationVisibility.getFormedMemberTier(world, x, y, z));
            }

            String formedModelName = block.getFormedModelName();
            if (formedModelName != null) {
                return formedModelName;
            }
        }

        if (!(block instanceof BlockECOController)) {
            return block.getModelName();
        }

        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileECOController && ((TileECOController) tile).isFormed()) {
            TileECOController controller = (TileECOController) tile;
            BlockECOController controllerBlock = (BlockECOController) block;
            String formedModelName = controller.isMirrored() ? controllerBlock.getMirroredFormedModelName()
                : controllerBlock.getFormedModelName();
            if (formedModelName != null) {
                return formedModelName;
            }
        }
        return block.getModelName();
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return this.renderId;
    }
}
