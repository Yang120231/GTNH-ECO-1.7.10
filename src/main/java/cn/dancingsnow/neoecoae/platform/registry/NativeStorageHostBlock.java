package cn.dancingsnow.neoecoae.platform.registry;

import java.util.ArrayList;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

public final class NativeStorageHostBlock extends NativeMachineBlock {

    public static final Property<ECOStorageInterfaceMode> STORAGE_MODE = NativeInterfaceBlock.STORAGE_MODE;

    public NativeStorageHostBlock(BlockEntityType<?> type, String model) {
        super(type, null, model);
        define(STORAGE_MODE, ECOStorageInterfaceMode.STORAGE);
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean harvest) {
        if (harvest && world.getTileEntity(x, y, z) instanceof ECOStorageSystemBlockEntity host
            && host.hasTransferRemainder()) return true;
        return super.removedByPlayer(world, player, x, y, z, harvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int metadata) {
        boolean heldForDrop = world.getTileEntity(x, y, z) instanceof ECOStorageSystemBlockEntity host
            && host.hasTransferRemainder();
        super.harvestBlock(world, player, x, y, z, metadata);
        if (heldForDrop) world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = super.getDrops(world, x, y, z, metadata, fortune);
        if (!world.isRemote && !drops.isEmpty()
            && world.getTileEntity(x, y, z) instanceof ECOStorageSystemBlockEntity host
            && host.hasTransferRemainder()) host.saveTransferRemainderToItem(drops.get(0));
        return drops;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, entity, stack);
        if (!world.isRemote && stack != null
            && world.getTileEntity(x, y, z) instanceof ECOStorageSystemBlockEntity host)
            host.restoreTransferRemainderFromItem(stack);
    }

    public static boolean isPlayerCloseEnough(WorldView world, BlockPos pos, EntityPlayer player) {
        return player != null && player.worldObj == world.nativeWorld()
            && player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
