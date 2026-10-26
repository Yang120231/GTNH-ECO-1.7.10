package cn.dancingsnow.neoecoae.platform.registry;

import java.util.ArrayList;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts.Role;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

/** Keep the actual tile alive through Forge harvest so owned inventories can move to one recovery carrier. */
public final class NativeExecutionMachineBlock extends NativeMachineBlock {

    public static final Property<Boolean> WORKING = Property.bool("working");

    public NativeExecutionMachineBlock(BlockEntityType<?> type, Role role, String model) {
        super(type, role, model);
        // Imported worker and threading-core blockstates require this even for inventory rendering.
        if (role == Role.CRAFTING_WORKER || role == Role.COMPUTATION_THREADING_CORE) define(WORKING, false);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        return willHarvest || super.removedByPlayer(world, player, x, y, z, false);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int metadata) {
        super.harvestBlock(world, player, x, y, z, metadata);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = super.getDrops(world, x, y, z, metadata, fortune);
        if (world.isRemote) return drops;
        var tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof NativeNetworkBlockEntity)) return drops;
        if (tile instanceof ECOComputationThreadingCoreBlockEntity) {
            ItemStack carrier = new ItemStack(this);
            ((ECOComputationThreadingCoreBlockEntity) tile).packExactInventories(carrier);
            drops.clear();
            drops.add(carrier);
        }
        ((NativeNetworkBlockEntity) tile).addAdditionalDrops(WorldView.of(world), new BlockPos(x, y, z), drops);
        return drops;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, entity, stack);
        if (world.isRemote || stack == null || !stack.hasTagCompound()) return;
        var tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof NativeNetworkBlockEntity)) return;
        var nativeTile = (NativeNetworkBlockEntity) tile;
        if (stack.getTagCompound()
            .hasKey("BlockEntityTag", 10))
            nativeTile.loadTag(
                new cn.dancingsnow.neoecoae.platform.nbt.CompoundTag(
                    stack.getTagCompound()
                        .getCompoundTag("BlockEntityTag")),
                RegistryAccess.EMPTY);
        if (tile instanceof ECOComputationThreadingCoreBlockEntity)
            ((ECOComputationThreadingCoreBlockEntity) tile).restoreExactInventories(stack);
        nativeTile.saveChanges();
    }
}
