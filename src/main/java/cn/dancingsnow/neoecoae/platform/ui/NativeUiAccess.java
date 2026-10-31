package cn.dancingsnow.neoecoae.platform.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;

/** The same physical tile and open menu must still own every deferred edit. */
public final class NativeUiAccess {

    private NativeUiAccess() {}

    public static boolean canEdit(TileEntity tile, EntityPlayer player, Container container) {
        return tile != null && player != null
            && container != null
            && player.openContainer == container
            && container.canInteractWith(player)
            && !tile.isInvalid()
            && tile.getWorldObj() != null
            && !tile.getWorldObj().isRemote
            && player.worldObj == tile.getWorldObj()
            && (player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) <= 64
                || cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm.ECORemoteGuiOpener
                    .isRemoteTarget(player, tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord))
            && tile.getWorldObj()
                .getChunkProvider()
                .chunkExists(tile.xCoord >> 4, tile.zCoord >> 4)
            && tile.getWorldObj()
                .getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile;
    }
}
