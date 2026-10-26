package cn.dancingsnow.neoecoae.platform.multiblock;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;

import cn.dancingsnow.neoecoae.multiblock.placement.WorldPlannedBlock;
import cn.dancingsnow.neoecoae.platform.inventory.IItemHandler;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

/** Native permission/events/sounds and stack capability boundary for the unchanged placement state machine. */
public final class NativePlacementPlatform {

    private NativePlacementPlatform() {}

    public static EntityPlayerMP findPlayer(java.util.UUID id) {
        var server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) return null;
        for (Object value : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) value;
            if (player.getUniqueID()
                .equals(id)) return player;
        }
        return null;
    }

    /** A target item can expose its real contained inventory, since Forge 1.7.10 has no item-handler capability. */
    public interface ItemInventoryProvider {

        IItemHandler itemInventory(ItemStack stack);
    }

    public static IItemHandler itemInventory(ItemStack stack) {
        return !ItemStacks.isEmpty(stack) && stack.getItem() instanceof ItemInventoryProvider
            ? ((ItemInventoryProvider) stack.getItem()).itemInventory(stack)
            : null;
    }

    public static boolean replaceable(WorldView level, BlockPos pos, BlockState state) {
        var world = level.nativeWorld();
        return state.getBlock()
            .isAir(world, pos.getX(), pos.getY(), pos.getZ())
            || state.getBlock()
                .isReplaceable(world, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean insideWorldBounds(BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < 256
            && pos.getX() >= -29_999_984
            && pos.getX() < 29_999_984
            && pos.getZ() >= -29_999_984
            && pos.getZ() < 29_999_984;
    }

    public static boolean mayInteract(ServerWorldView level, EntityPlayerMP player, WorldPlannedBlock planned) {
        BlockPos pos = planned.worldPos();
        return player.worldObj == level.nativeWorld() && level.nativeWorld()
            .canMineBlock(player, pos.getX(), pos.getY(), pos.getZ())
            && player.canPlayerEdit(pos.getX(), pos.getY(), pos.getZ(), 1, planned.requiredItem());
    }

    public static boolean placeWithPermissionCheck(ServerWorldView level, WorldPlannedBlock planned,
        EntityPlayerMP player) {
        BlockPos pos = planned.worldPos();
        var world = level.nativeWorld();
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, pos.getX(), pos.getY(), pos.getZ(), 3);
        var against = world.getBlock(pos.getX(), pos.getY() - 1, pos.getZ());
        if (!level.setBlock(pos, planned.targetState(), 3)) return false;
        BlockEvent.PlaceEvent event = new BlockEvent.PlaceEvent(snapshot, against, player);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            // 1.7's default restore refuses a changed block; a cancelled placement must restore the captured state/NBT.
            snapshot.restore(true);
            return false;
        }
        return true;
    }

    public static void playPlacementSound(ServerWorldView level, WorldPlannedBlock planned) {
        var sound = planned.targetState()
            .getBlock().stepSound;
        var pos = planned.worldPos();
        level.nativeWorld()
            .playSoundEffect(
                pos.getX() + .5,
                pos.getY() + .5,
                pos.getZ() + .5,
                sound.func_150496_b(),
                (sound.getVolume() + 1) / 2,
                sound.getPitch() * .8f);
    }
}
