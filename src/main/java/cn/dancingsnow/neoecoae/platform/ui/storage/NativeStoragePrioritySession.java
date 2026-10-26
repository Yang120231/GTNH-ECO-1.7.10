package cn.dancingsnow.neoecoae.platform.ui.storage;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.platform.ui.NativeTileSession;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Source priority arithmetic, validated against the still-open physical host menu. */
public final class NativeStoragePrioritySession extends NativeTileSession {

    private final ECOStorageSystemBlockEntity host;
    private int priority;
    private static volatile NativeStoragePrioritySession inspection;

    public NativeStoragePrioritySession(ECOStorageSystemBlockEntity host) {
        super(host);
        this.host = host;
        if ((Boolean.getBoolean("neoecoae.storageUiAcceptance")
            || Boolean.getBoolean("neoecoae.storageUiNetworkAcceptance")) && host.getWorldObj().isRemote)
            inspection = this;
    }

    static NativeStoragePrioritySession acceptanceInspection() {
        return inspection;
    }

    public int priority() {
        return priority;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        int current = host.getStoragePriority();
        if (init || priority != current) {
            priority = current;
            syncToClient(0, output -> output.writeInt(priority));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Unknown storage priority response: " + id);
        priority = buffer.readInt();
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 0 && id != 1) throw new IOException("Unknown storage priority action: " + id);
        int value = buffer.readInt();
        if (buffer.isReadable()) throw new IOException("Trailing storage priority action bytes");
        edit(() -> host.setStoragePriority(id == 0 ? value : StoragePriority.adjust(host.getStoragePriority(), value)));
    }

    @SideOnly(Side.CLIENT)
    public void change(int delta) {
        syncToServer(1, output -> output.writeInt(delta));
    }

    @SideOnly(Side.CLIENT)
    public void set(String text) {
        if (text == null || text.isEmpty() || text.equals("-") || text.equals("+")) return;
        try {
            int value = Integer.parseInt(text);
            syncToServer(0, output -> output.writeInt(value));
        } catch (NumberFormatException ignored) {}
    }
}
