package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.networking.IGrid;

/** The terminal slot that holds the encoded pattern, plus the credentials needed to edit it. */
public interface PatternUploadSource {

    PatternSlotAccess slots();

    int slot();

    /** Null when the terminal is offline. */
    IGrid grid();

    /** Network, power and security BUILD permission are re-checked on every call. */
    boolean canEdit(EntityPlayerMP player);

    /** Identity of the physical source inventory; receipts may only be undone into the same one. */
    Object identity();
}
