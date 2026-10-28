package cn.dancingsnow.neoecoae.platform.ui;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A server-owned snapshot and fixed-width action protocol for native machine pages. */
public abstract class NativeSnapshotSession extends NativeTileSession {

    private CompoundTag state = new CompoundTag(), previous;
    private final TileEntity tile;
    private long nextCapture;

    protected NativeSnapshotSession(TileEntity tile) {
        super(tile);
        this.tile = tile;
    }

    public final CompoundTag state() {
        return state;
    }

    protected abstract CompoundTag capture();

    protected abstract void apply(int action, int argument);

    @Override
    public final void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        long tick = tile.getWorldObj()
            .getTotalWorldTime();
        if (!init && tick < nextCapture) return;
        nextCapture = tick + 5;
        publish(init);
    }

    private void publish(boolean force) {
        state = capture();
        if (force || !state.equals(previous)) {
            previous = state.copy();
            CompoundTag snapshot = state;
            syncToClient(0, buffer -> new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).writeNbt(snapshot));
        }
    }

    /** Applies one validated action and republishes at once, so the player never waits for the next 5-tick capture. */
    final void applyAndPublish(int action, int argument) {
        apply(action, argument);
        publish(false);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public final void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Unknown machine snapshot: " + id);
        state = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
        if (state == null || buffer.isReadable()) throw new IOException("Invalid machine snapshot");
    }

    @Override
    public final void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 0 || buffer.readableBytes() != 8) throw new IOException("Invalid machine action");
        int action = buffer.readInt(), argument = buffer.readInt();
        edit(() -> applyAndPublish(action, argument));
    }

    @SideOnly(Side.CLIENT)
    public final void action(int action, int argument) {
        syncToServer(0, buffer -> {
            buffer.writeInt(action);
            buffer.writeInt(argument);
        });
    }
}
