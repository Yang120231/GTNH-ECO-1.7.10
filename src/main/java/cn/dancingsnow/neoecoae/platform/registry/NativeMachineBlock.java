package cn.dancingsnow.neoecoae.platform.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAEMultiBlock;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockStateProperties;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.minecraft.NativeStateBlock;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

/** Forge block identity, orientation metadata and tile factory. Formation stays in imported entities/calculators. */
public class NativeMachineBlock extends BlockContainer implements NativeStateBlock, NativeModelIdentity {

    public static int renderId;
    public static final Property<Boolean> FORMED = Property.bool("formed"), INVISIBLE = Property.bool("invisible"),
        CLASSIC_VISIBLE = Property.bool("classic_visible"), MIRRORED = Property.bool("mirrored"),
        HAS_CELL = Property.bool("has_cell");
    public static final Property<Boolean> NETWORK_SWITCH = Property.bool("network_switch"),
        HIGH_ENERGY_NETWORK_SWITCH = Property.bool("high_energy_network_switch");
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final BlockEntityType<?> type;
    private final ECOMultiblockParts.Role role;
    private final String model;
    private BlockState defaultState;

    public NativeMachineBlock(BlockEntityType<?> type, ECOMultiblockParts.Role role, String model) {
        super(Material.iron);
        this.type = type;
        this.role = role;
        this.model = model;
        setHardness(4);
        setResistance(10);
        Map<Property<?>, Object> properties = new LinkedHashMap<>();
        properties.put(FACING, Direction.NORTH);
        properties.put(FORMED, false);
        properties.put(NETWORK_SWITCH, false);
        properties.put(HIGH_ENERGY_NETWORK_SWITCH, false);
        properties.put(INVISIBLE, false);
        properties.put(CLASSIC_VISIBLE, false);
        properties.put(MIRRORED, false);
        properties.put(HAS_CELL, false);
        defaultState = new BlockState(this, properties);
    }

    public ECOMultiblockParts.Role partRole() {
        return role;
    }

    public String model() {
        return model;
    }

    @Override
    public String getLocalizedName() {
        return net.minecraft.util.StatCollector.translateToLocal("block.neoecoae." + type.id() + ".name");
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
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
        blockIcon = cn.dancingsnow.neoecoae.platform.render.NativeMachineRendering.register(this, register);
    }

    protected final <T> void define(Property<T> property, T value) {
        Map<Property<?>, Object> values = new LinkedHashMap<>(defaultState.getValues());
        values.put(property, value);
        defaultState = new BlockState(this, values);
    }

    @Override
    public BlockState defaultBlockState() {
        return defaultState;
    }

    @Override
    public BlockState fromMetadata(int metadata) {
        Direction facing = metadata >= 2 && metadata <= 5 ? Direction.values()[metadata] : Direction.NORTH;
        return defaultState.setValue(FACING, facing);
    }

    @Override
    public int metadata(BlockState state) {
        return state.getValue(FACING)
            .ordinal();
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return type.create();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, net.minecraft.entity.player.EntityPlayer player,
        int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking() || !(world.getTileEntity(x, y, z) instanceof com.cleanroommc.modularui.api.IGuiHolder))
            return false;
        if (!world.isRemote) com.cleanroommc.modularui.factory.GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        int quadrant = net.minecraft.util.MathHelper.floor_double(entity.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        Direction facing = new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST }[quadrant];
        WorldView.of(world)
            .setBlockAndUpdate(new BlockPos(x, y, z), defaultState.setValue(FACING, facing));
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof appeng.tile.AEBaseTile) ((appeng.tile.AEBaseTile) tile)
            .setOrientation(facing.nativeSide(), net.minecraftforge.common.util.ForgeDirection.UP);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!world.isRemote && tile instanceof IAEMultiBlock)
            ((IAEMultiBlock<?>) tile).updateMultiBlock(new BlockPos(x, y, z));
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!world.isRemote && tile instanceof IAEMultiBlock) ((IAEMultiBlock<?>) tile).breakCluster();
        super.breakBlock(world, x, y, z, block, metadata);
    }
}
