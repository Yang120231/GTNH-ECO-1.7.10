package cn.dancingsnow.neoecoae.platform.registry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

public final class NativeDriveBlock extends NativeMachineBlock {

    public NativeDriveBlock(BlockEntityType<?> type, String model) {
        super(type, ECOMultiblockParts.Role.STORAGE_DRIVE, model);
    }

    /**
     * Source ECODriveBlock#useItemOn / #useWithoutItem: right-clicking an empty drive with a cell it accepts installs
     * one copy (creative keeps the held stack); sneak-right-clicking with an empty hand returns the installed cell
     * unless an infinite-storage lock blocks extraction, in which case the reason is shown instead.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        var tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof ECODriveBlockEntity))
            return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
        var drive = (ECODriveBlockEntity) tile;
        ItemStack held = player.getHeldItem();
        // The source never reaches useItemOn while sneaking with an item in hand (secondary use).
        if (held != null && !player.isSneaking() && drive.getCellStack() == null && drive.isItemValid(held)) {
            if (!world.isRemote) {
                // Same entry point as GUI/automation insertion: persistence, client sync, HAS_CELL and AE2 refresh.
                drive.setCellStack(ItemStacks.copyWithCount(held, 1));
                if (drive.getCellStack() != null && !player.capabilities.isCreativeMode && --held.stackSize <= 0)
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
            return true;
        }
        if (held == null && player.isSneaking() && drive.getCellStack() != null) {
            if (!world.isRemote) {
                var reason = drive.getCellExtractionBlockReason();
                if (reason != ECODriveBlockEntity.CellExtractionBlockReason.NONE) {
                    player.addChatComponentMessage(new ChatComponentTranslation(reason.translationKey()));
                    return true;
                }
                ItemStack cell = drive.getCellStack();
                drive.setCellStack(null);
                player.inventory.setInventorySlotContents(player.inventory.currentItem, cell);
            }
            return true;
        }
        return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
    }
}
