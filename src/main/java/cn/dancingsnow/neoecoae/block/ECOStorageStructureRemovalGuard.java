package cn.dancingsnow.neoecoae.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOController;

final class ECOStorageStructureRemovalGuard {

    private static final String MESSAGE_KEY = "chat.neoecoae.storage.infinite_remove_blocked";

    private ECOStorageStructureRemovalGuard() {}

    static boolean canRemove(World world, int x, int y, int z) {
        return findBlockingController(world, x, y, z) == null;
    }

    static boolean canRemoveOrNotify(World world, EntityPlayer player, int x, int y, int z) {
        TileECOController controller = findBlockingController(world, x, y, z);
        if (controller == null) {
            return true;
        }
        if (!world.isRemote && player != null) {
            player.addChatMessage(new ChatComponentTranslation(MESSAGE_KEY));
        }
        return false;
    }

    private static TileECOController findBlockingController(World world, int x, int y, int z) {
        if (world == null || world.isRemote) {
            return null;
        }
        for (Object tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileECOController)) {
                continue;
            }
            TileECOController controller = (TileECOController) tile;
            if (controller.getSubsystem() == ECOControllerSubsystem.STORAGE
                && controller.protectsWorldPosition(x, y, z)
                && !isSafelyRemovableController(controller, x, y, z)) {
                return controller;
            }
        }
        return null;
    }

    private static boolean isSafelyRemovableController(TileECOController controller, int x, int y, int z) {
        return controller.xCoord == x && controller.yCoord == y && controller.zCoord == z
            && controller.canRemoveFromWorld();
    }
}
