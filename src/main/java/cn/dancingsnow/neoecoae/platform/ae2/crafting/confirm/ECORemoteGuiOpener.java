package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;

/**
 * Opens the native GUI of a provider or machine the player chose in the crafting status menu, without walking there.
 * Every GUI stack builds its client screen from the client's own copy of the block, so the target must be in the
 * player's dimension and inside the chunks the player's client is watching. The 8-block reach checks of ModularUI2
 * screens are lifted only for the one container opened here, and only while it stays the player's open container.
 */
public final class ECORemoteGuiOpener {

    private static final Map<EntityPlayer, Marker> MARKERS = new WeakHashMap<>();

    private ECORemoteGuiOpener() {}

    private static final class Marker {

        final World world;
        final int x, y, z;
        @Nullable
        Container container;

        Marker(World world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** @return null when the GUI opened, otherwise the translation key of the reason it could not */
    @Nullable
    public static synchronized String open(EntityPlayerMP player, TileEntity tile, @Nullable ForgeDirection side) {
        World world = tile.getWorldObj();
        if (world == null || tile.isInvalid()) return "gui.neoecoae.processing.open_unavailable";
        if (world != player.worldObj) return "gui.neoecoae.processing.other_dimension";
        if (!(world instanceof WorldServer server) || !server.getPlayerManager()
            .isPlayerWatchingChunk(player, tile.xCoord >> 4, tile.zCoord >> 4))
            return "gui.neoecoae.processing.out_of_view";
        Container before = player.openContainer;
        Marker marker = new Marker(world, tile.xCoord, tile.yCoord, tile.zCoord);
        MARKERS.put(player, marker);
        boolean attempted;
        try {
            attempted = dispatch(player, tile, side);
        } catch (RuntimeException | LinkageError failure) {
            MARKERS.remove(player);
            throw failure;
        }
        if (!attempted || player.openContainer == before) {
            MARKERS.remove(player);
            return attempted ? "gui.neoecoae.processing.open_unavailable" : "gui.neoecoae.processing.open_unsupported";
        }
        marker.container = player.openContainer;
        return null;
    }

    private static boolean dispatch(EntityPlayerMP player, TileEntity tile, @Nullable ForgeDirection side) {
        ForgeDirection face = side == null ? ForgeDirection.UNKNOWN : side;
        if (isInterface(tile, face)) {
            appeng.util.Platform.openGUI(player, tile, face, appeng.core.sync.GuiBridge.GUI_INTERFACE);
            return true;
        }
        if (NativeGregTech.openGui(tile, player)) return true;
        if (tile instanceof com.cleanroommc.modularui.api.IGuiHolder) {
            com.cleanroommc.modularui.factory.GuiFactories.tileEntity()
                .open(player, tile.xCoord, tile.yCoord, tile.zCoord);
            return true;
        }
        return false;
    }

    private static boolean isInterface(TileEntity tile, ForgeDirection side) {
        if (tile instanceof appeng.helpers.IInterfaceHost) return true;
        return tile instanceof appeng.api.parts.IPartHost host && side != ForgeDirection.UNKNOWN
            && host.getPart(side) instanceof appeng.helpers.IInterfaceHost;
    }

    /**
     * True while {@code player} is using the container this class opened for the block at x/y/z. Consulted by the
     * reach checks; a container the player opened any other way never matches.
     */
    public static synchronized boolean isRemoteTarget(EntityPlayer player, World world, int x, int y, int z) {
        Marker marker = MARKERS.get(player);
        if (marker == null) return false;
        if (marker.container != null && player.openContainer != marker.container) {
            MARKERS.remove(player);
            return false;
        }
        if (marker.world != world || marker.x != x || marker.y != y || marker.z != z) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile != null && !tile.isInvalid();
    }

    public static synchronized void clear() {
        MARKERS.clear();
    }
}
