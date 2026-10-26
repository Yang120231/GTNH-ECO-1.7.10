package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/** The legacy client receives source job transitions through the native translated chat packet. */
public final class CraftingJobStatusPacket {

    public enum Status {
        STARTED,
        FINISHED,
        CANCELLED
    }

    private CraftingJobStatusPacket() {}

    public static void send(EntityPlayerMP player, UUID job, GenericStack output, long remaining, Status status) {
        player.addChatMessage(
            new ChatComponentTranslation(
                "neoecoae.crafting.status." + status.name()
                    .toLowerCase(java.util.Locale.ROOT),
                output.what()
                    .getDisplayName(),
                output.amount(),
                remaining,
                job.toString()));
    }
}
