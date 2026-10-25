package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.Objects;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Installs one cursor-held disk into a verified empty physical drive. */
public final class StorageCellUiInsertion {

    private StorageCellUiInsertion() {}

    public static boolean insert(ECOStorageSystemBlockEntity host, EntityPlayer player, BlockPos position,
        ItemStack expected) {
        if (player.worldObj.isRemote || !host.isFormed()
            || host.getCluster() == null
            || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(expected)) return false;
        var menu = player.openContainer;
        ItemStack carried = player.inventory.getItemStack();
        if (!ItemStack.areItemStacksEqual(expected, carried)) return false;
        ECODriveBlockEntity drive = host.getCluster()
            .getDrives()
            .stream()
            .filter(candidate -> !candidate.isRemoved() && Objects.equals(candidate.getBlockPos(), position))
            .findFirst()
            .orElse(null);
        if (drive == null
            || drive.getCellStack() != null
                && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(drive.getCellStack())
            || !drive.canExtractCell()
            || !drive.isItemValid(carried)) return false;

        ItemStack installed = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(carried, 1);
        drive.setCellStack(installed);
        // The real setter retains this reference; a rejected installation must not consume the cursor item.
        if (drive.getCellStack() != installed) return false;
        cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.shrink(carried, 1);
        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(carried)) player.inventory.setItemStack(null);
        cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
        return true;
    }
}
