package cn.dancingsnow.neoecoae.platform.registry;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeStateBlock;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.CollisionContext;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.Shapes;
import cn.dancingsnow.neoecoae.platform.minecraft.shape.VoxelShape;

/** Native identity and physics for the source's ordinary blocks; shape data remains in the imported block class. */
public class NativeSimpleModelBlock extends Block implements NativeStateBlock, NativeModelIdentity {

    public static int renderId;
    private final NativeBlockProperties properties;

    public NativeSimpleModelBlock(NativeBlockProperties properties) {
        super(properties.base.getMaterial());
        this.properties = properties;
        setHardness(properties.hardness);
        setResistance(properties.resistance * 5 / 3);
        setStepSound(properties.base.stepSound);
        setHarvestLevel("pickaxe", properties.harvestLevel);
        setBlockName("neoecoae." + properties.id);
        setCreativeTab(NativeContent.TAB);
    }

    @Override
    public String model() {
        return properties.id;
    }

    @Override
    public String getLocalizedName() {
        return net.minecraft.util.StatCollector.translateToLocal("block.neoecoae." + model() + ".name");
    }

    @Override
    public BlockState defaultBlockState() {
        return new BlockState(this, java.util.Collections.emptyMap());
    }

    @Override
    public BlockState fromMetadata(int metadata) {
        return defaultBlockState();
    }

    @Override
    public int metadata(BlockState state) {
        return 0;
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
        return properties != null && !properties.frame && !properties.noOcclusion;
    }

    @Override
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public boolean canRenderInPass(int pass) {
        return pass == 0 || pass == 1;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister register) {
        blockIcon = cn.dancingsnow.neoecoae.platform.render.NativeMaterialBlockRendering.register(this, register);
    }

    protected VoxelShape getShape(BlockState state, WorldView level, BlockPos position, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List boxes,
        Entity entity) {
        for (var box : getShape(
            defaultBlockState(),
            WorldView.of(world),
            new BlockPos(x, y, z),
            new CollisionContext(entity)).boxes()) {
            AxisAlignedBB nativeBox = box.at(x, y, z);
            if (mask.intersectsWith(nativeBox)) boxes.add(nativeBox);
        }
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 from, Vec3 to) {
        MovingObjectPosition closest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var box : getShape(
            defaultBlockState(),
            WorldView.of(world),
            new BlockPos(x, y, z),
            new CollisionContext(null)).boxes()) {
            MovingObjectPosition hit = box.at(x, y, z)
                .calculateIntercept(from, to);
            if (hit != null && from.squareDistanceTo(hit.hitVec) < distance) {
                closest = new MovingObjectPosition(x, y, z, hit.sideHit, hit.hitVec);
                distance = from.squareDistanceTo(hit.hitVec);
            }
        }
        return closest;
    }

    @Override
    public Item getItemDropped(int metadata, Random random, int fortune) {
        return properties.oreDrop == null ? Item.getItemFromBlock(this)
            : java.util.Objects.requireNonNull(NativeContent.item(properties.oreDrop));
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, Random random) {
        return properties.oreDrop == null || fortune <= 0 ? 1 : Math.max(0, random.nextInt(fortune + 2) - 1) + 1;
    }

    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int metadata) {
        return true;
    }
}
