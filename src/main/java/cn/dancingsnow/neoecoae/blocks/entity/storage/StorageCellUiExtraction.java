package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Moves a single physical cell after validating its installation identity and destination. */
public final class StorageCellUiExtraction {

    private StorageCellUiExtraction() {}

    public static boolean extract(ECOStorageSystemBlockEntity host, EntityPlayer player, BlockPos position,
        UUID expected, boolean toInventory) {
        if (player.worldObj.isRemote || !host.isFormed() || host.getCluster() == null || expected == null) return false;
        ECODriveBlockEntity drive = host.getCluster()
            .getDrives()
            .stream()
            .filter(candidate -> !candidate.isRemoved() && Objects.equals(candidate.getBlockPos(), position))
            .findFirst()
            .orElse(null);
        if (drive == null || !expected.equals(drive.getCellInteractionId())
            || drive.getCellStack() == null
            || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(drive.getCellStack())
            || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(drive.getCellStack()) != 1) return false;
        var reason = drive.getCellExtractionBlockReasonText();
        if (reason != null) {
            player.addChatMessage(reason.nativeText());
            return false;
        }
        if (!drive.canExtractCell()) return false;

        var inventory = player.inventory;
        var menu = player.openContainer;
        int destination = -1;
        if (toInventory) {
            for (int i = 0; i < inventory.mainInventory.length; i++) {
                ItemStack existing = inventory.getStackInSlot(i);
                if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(existing)) {
                    destination = i;
                    break;
                }
            }
            if (destination < 0) return false;
        } else {
            if (!cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(player.inventory.getItemStack()))
                return false;
        }

        // Retain the actual stack: unmounting may persist its latest cell components in place.
        ItemStack cell = drive.getCellStack();
        drive.setCellStack(null);
        if (drive.getCellStack() != null) return false;
        if (toInventory) {
            inventory.setInventorySlotContents(destination, cell);
            inventory.markDirty();
        } else {
            player.inventory.setItemStack(cell);
        }
        cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
        return true;
    }
}
