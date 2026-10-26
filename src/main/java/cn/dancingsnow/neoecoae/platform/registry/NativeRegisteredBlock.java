package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.google.gson.JsonObject;

/** Registration and source artwork only; no substitute CPU, cluster, inventory or processing logic. */
public final class NativeRegisteredBlock extends NativeSimpleModelBlock {

    private final String sourceModel;
    private final boolean cross;

    public NativeRegisteredBlock(JsonObject definition) {
        super(new NativeBlockProperties(definition));
        sourceModel = definition.get("model")
            .getAsString();
        cross = definition.get("cross")
            .getAsBoolean();
        if (cross) {
            float inset = definition.get("inset")
                .getAsInt() / 16F;
            setBlockBounds(
                inset,
                0,
                inset,
                1 - inset,
                definition.get("height")
                    .getAsInt() / 16F,
                1 - inset);
            setLightLevel(
                definition.get("light")
                    .getAsInt() / 15F);
        }
    }

    public String sourceModel() {
        return sourceModel;
    }

    @Override
    public int getRenderType() {
        return cross ? 1 : super.getRenderType();
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return cross ? AxisAlignedBB.getBoundingBox(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ)
            : super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    protected cn.dancingsnow.neoecoae.platform.minecraft.shape.VoxelShape getShape(
        cn.dancingsnow.neoecoae.platform.minecraft.BlockState state,
        cn.dancingsnow.neoecoae.platform.minecraft.WorldView level,
        cn.dancingsnow.neoecoae.platform.minecraft.BlockPos position,
        cn.dancingsnow.neoecoae.platform.minecraft.shape.CollisionContext context) {
        return cross
            ? cn.dancingsnow.neoecoae.platform.minecraft.shape.Shapes
                .box(minX * 16, minY * 16, minZ * 16, maxX * 16, maxY * 16, maxZ * 16)
            : super.getShape(state, level, position, context);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister register) {
        if (cross) blockIcon = register.registerIcon("neoecoae:" + model());
        else super.registerBlockIcons(register);
    }
}
