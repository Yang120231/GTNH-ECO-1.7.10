package cn.dancingsnow.neoecoae.platform.ui.storage;

import java.io.IOException;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.StorageCellPriority;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.ui.NativeTileSession;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Source priority arithmetic, validated against the still-open physical host menu. */
public final class NativeStoragePrioritySession extends NativeTileSession {

    private final ECOStorageSystemBlockEntity host;
    private int priority;
    private BlockPos position;
    private UUID installation;
    private boolean editable = true;
    private boolean previousEditable;
    private String previousTarget;
    private int targetRevision;
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

    public boolean editable() { return editable; }

    public Component title() {
        return Component.translatable(position == null ? "gui.appliedenergistics2.Priority"
            : "gui.neoecoae.storage.cell_priority.title");
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        var drive = position == null ? null : StorageCellPriority.resolve(host, position, installation);
        boolean allowed = position == null || drive != null;
        int current = position == null ? host.getStoragePriority() : drive == null ? 0 : drive.getConfiguredCellPriority();
        String target = position == null ? "host" : position.asLong() + ":" + installation;
        if (init || priority != current || allowed != previousEditable || !target.equals(previousTarget)) {
            priority = current;
            editable = previousEditable = allowed;
            previousTarget = target;
            syncToClient(0, output -> {
                output.writeInt(targetRevision);
                writeTarget(output);
                output.writeInt(current);
                output.writeBoolean(allowed);
            });
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Unknown storage priority response: " + id);
        int revision = buffer.readInt();
        BlockPos responsePosition = buffer.readBoolean() ? BlockPos.of(buffer.readLong()) : null;
        UUID responseInstallation = responsePosition == null ? null : new UUID(buffer.readLong(), buffer.readLong());
        int value = buffer.readInt();
        boolean allowed = buffer.readBoolean();
        if (buffer.isReadable()) throw new IOException("Trailing storage priority response bytes");
        if (revision != targetRevision) return;
        position = responsePosition;
        installation = responseInstallation;
        priority = value;
        editable = allowed;
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id < 0 || id > 2) throw new IOException("Unknown storage priority action: " + id);
        int revision = buffer.readInt();
        BlockPos targetPosition = buffer.readBoolean() ? BlockPos.of(buffer.readLong()) : null;
        UUID targetInstallation = targetPosition == null ? null : new UUID(buffer.readLong(), buffer.readLong());
        int value = id == 2 ? 0 : buffer.readInt();
        if (buffer.isReadable()) throw new IOException("Trailing storage priority action bytes");
        edit(() -> {
            // Every edit carries its physical target; reordered UI opens cannot retarget an old edit.
            position = targetPosition;
            installation = targetInstallation;
            targetRevision = revision;
            if (id != 2) {
                if (position == null) host.setStoragePriority(id == 0 ? value : StoragePriority.adjust(host.getStoragePriority(), value));
                else {
                    var drive = StorageCellPriority.resolve(host, position, installation);
                    if (drive != null) drive.setConfiguredCellPriority(id == 0 ? value
                        : StoragePriority.adjust(drive.getConfiguredCellPriority(), value));
                }
            }
            detectAndSendChanges(true);
        });
    }

    private void writeTarget(PacketBuffer buffer) {
        buffer.writeBoolean(position != null);
        if (position != null) {
            buffer.writeLong(position.asLong());
            buffer.writeLong(installation.getMostSignificantBits());
            buffer.writeLong(installation.getLeastSignificantBits());
        }
    }

    private void writeRequest(PacketBuffer buffer) {
        buffer.writeInt(targetRevision);
        writeTarget(buffer);
    }

    @SideOnly(Side.CLIENT)
    public void selectHost() {
        position = null;
        installation = null;
        editable = true;
        targetRevision++;
        syncToServer(2, this::writeRequest);
    }

    @SideOnly(Side.CLIENT)
    public boolean selectCell(StorageHostUI.CellEntry cell) {
        if (cell.position() == null || cell.interactionId() == null || !ECOStorageCellSettings.canConfigure(cell.stack())) return false;
        position = cell.position();
        installation = cell.interactionId();
        priority = ECOStorageCellSettings.priority(cell.stack());
        editable = true;
        targetRevision++;
        syncToServer(2, this::writeRequest);
        return true;
    }

    @SideOnly(Side.CLIENT)
    public void change(int delta) {
        if (editable) syncToServer(1, output -> { writeRequest(output); output.writeInt(delta); });
    }

    @SideOnly(Side.CLIENT)
    public void set(String text) {
        if (!editable || text == null || text.isEmpty() || text.equals("-") || text.equals("+")) return;
        try {
            int value = Integer.parseInt(text);
            syncToServer(0, output -> { writeRequest(output); output.writeInt(value); });
        } catch (NumberFormatException ignored) {}
    }
}
