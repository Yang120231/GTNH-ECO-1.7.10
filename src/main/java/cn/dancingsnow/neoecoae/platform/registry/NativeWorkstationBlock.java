package cn.dancingsnow.neoecoae.platform.registry;

import java.util.ArrayList;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts.Role;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NativeNetworkBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Preserve queued long quantities, tank contents and provider settings in one mined machine item. */
public final class NativeWorkstationBlock extends NativeMachineBlock {

    public NativeWorkstationBlock(BlockEntityType<?> type, Role role, String model) {
        super(type, role, model);
        if (role == Role.CRAFTING_WORKER) {
            define(WorkstationMachines.WORKING, false);
            define(WorkstationMachines.MODULE, WorkstationMachines.Module.REGULAR);
        }
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean harvest) {
        return harvest || super.removedByPlayer(world, player, x, y, z, false);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int metadata) {
        super.harvestBlock(world, player, x, y, z, metadata);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = super.getDrops(world, x, y, z, metadata, fortune);
        if (world.isRemote || !(world.getTileEntity(x, y, z) instanceof NativeNetworkBlockEntity)) return drops;
        NativeNetworkBlockEntity tile = (NativeNetworkBlockEntity) world.getTileEntity(x, y, z);
        ItemStack carrier = new ItemStack(this);
        tile.saveToItem(carrier, RegistryAccess.EMPTY);
        drops.clear();
        drops.add(carrier);
        return drops;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, entity, stack);
        if (world.isRemote || stack == null
            || !stack.hasTagCompound()
            || !stack.getTagCompound()
                .hasKey("BlockEntityTag", 10))
            return;
        if (world.getTileEntity(x, y, z) instanceof NativeNetworkBlockEntity) {
            NativeNetworkBlockEntity tile = (NativeNetworkBlockEntity) world.getTileEntity(x, y, z);
            tile.loadTag(
                new CompoundTag(
                    stack.getTagCompound()
                        .getCompoundTag("BlockEntityTag")),
                RegistryAccess.EMPTY);
            tile.saveChanges();
        }
    }
}
