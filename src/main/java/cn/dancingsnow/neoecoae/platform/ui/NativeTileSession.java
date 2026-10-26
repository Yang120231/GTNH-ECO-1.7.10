package cn.dancingsnow.neoecoae.platform.ui;

import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.value.sync.SyncHandler;

import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Common world/menu ownership check for source page actions queued onto the server tick. */
public abstract class NativeTileSession extends SyncHandler {

    private final TileEntity tile;
    private final ServerTasks.Session serverSession = ServerTasks.captureSession();

    protected NativeTileSession(TileEntity tile) {
        this.tile = tile;
    }

    protected final boolean validSession() {
        return isValid() && NativeUiAccess.canEdit(tile, getSyncManager().getPlayer(), getSyncManager().getContainer());
    }

    protected final void edit(Runnable action) {
        serverSession.execute(() -> { if (validSession()) action.run(); });
    }
}
