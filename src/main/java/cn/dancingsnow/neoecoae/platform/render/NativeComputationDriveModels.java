package cn.dancingsnow.neoecoae.platform.render;

import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cn.dancingsnow.neoecoae.api.computation.ECOComputationCells;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.EcoModelRenderer;
import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.client.render.model.ModernModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;

/** High-version drive transforms, baked once like main's cells and cables into the native chunk mesh. */
final class NativeComputationDriveModels {

    private final BakedEcoModel[][] cells = new BakedEcoModel[3][2];
    private final BakedEcoModel[][][] cables = new BakedEcoModel[3][2][2];

    NativeComputationDriveModels(NativeMachineRendering.BlockModels owner, IIconRegister register) {
        String[] tiers = { "l4", "l6", "l9" };
        for (int tier = 0; tier < tiers.length; tier++) {
            for (int active = 0; active < 2; active++) {
                ModernModel cell = ModernModelLoader
                    .loadBlockModel("compute/cell_" + tiers[tier] + (active == 1 ? "_formed" : ""));
                cells[tier][active] = BakedEcoModel
                    .transformedSubModel(cell, NativeComputationDriveModels::transformInterior);
                owner.registerTextures(cells[tier][active], register);
                ModernModel cable = ModernModelLoader
                    .loadBlockModel("compute/cable_" + tiers[tier] + (active == 1 ? "" : "_dis"));
                for (int row = 0; row < 2; row++) {
                    boolean connected = active == 1;
                    boolean lower = row == 1;
                    cables[tier][active][row] = BakedEcoModel.transformedSubModel(
                        cable,
                        (vertex, facing) -> transformCable(vertex, facing, connected, lower));
                    owner.registerTextures(cables[tier][active][row], register);
                }
            }
        }
    }

    void render(ECOComputationDriveBlockEntity drive, BlockState state, ModelFacing facing, Map<String, IIcon> icons,
        IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
        boolean formed = state.getValue(NativeMachineBlock.FORMED);
        int driveTier = drive.getRenderTier();
        var memory = ECOComputationCells.describe(drive.getCellStack());
        boolean connected = formed && memory != null && driveTier >= memory.tier().getTier();
        if (memory != null) EcoModelRenderer.renderWorld(
            cells[tierIndex(memory.tier().getTier())][connected ? 1 : 0],
            facing,
            icons,
            world,
            x,
            y,
            z,
            block,
            renderer);
        if (!formed) return;
        int cableTier = connected ? memory.tier().getTier() : driveTier;
        EcoModelRenderer.renderWorld(
            cables[tierIndex(cableTier)][connected ? 1 : 0][drive.isLowerDrive() ? 1 : 0],
            facing,
            icons,
            world,
            x,
            y,
            z,
            block,
            renderer);
    }

    private static int tierIndex(int tier) {
        return Math.max(0, Math.min(2, tier - 1));
    }

    private static double[] transformInterior(double[] vertex, ModelFacing facing) {
        double x = vertex[0];
        double z = vertex[2];
        if (facing == ModelFacing.EAST) {
            x = -vertex[2];
            z = vertex[0];
        } else if (facing == ModelFacing.SOUTH) {
            x = -vertex[0];
            z = -vertex[2];
        } else if (facing == ModelFacing.WEST) {
            x = vertex[2];
            z = -vertex[0];
        }
        return new double[] { x + 0.5D + 0.25D * facing.getDirection().offsetX, vertex[1] + 0.5D,
            z + 0.5D + 0.25D * facing.getDirection().offsetZ };
    }

    private static double[] transformCable(double[] vertex, ModelFacing facing, boolean connected, boolean lower) {
        double x = vertex[0];
        double y = vertex[1];
        double z = vertex[2];
        if (connected) {
            if (lower) {
                x = -x;
                y = -y;
            }
            z -= 0.35D;
        } else {
            y += lower ? 0.688D : -0.688D;
            z -= 0.3D;
        }
        return transformInterior(new double[] { x, y, z }, facing);
    }
}
