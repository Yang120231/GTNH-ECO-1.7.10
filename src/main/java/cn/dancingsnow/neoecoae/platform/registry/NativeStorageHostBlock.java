package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.entity.player.EntityPlayer;

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

    public static boolean isPlayerCloseEnough(WorldView world, BlockPos pos, EntityPlayer player) {
        return player != null && player.worldObj == world.nativeWorld()
            && player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
