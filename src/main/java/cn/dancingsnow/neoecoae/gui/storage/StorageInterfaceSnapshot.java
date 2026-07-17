package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.storage.domain.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import io.netty.buffer.ByteBuf;

/** Immutable client view of the 1.20.1 storage-interface state. */
public final class StorageInterfaceSnapshot {

    public static final StorageInterfaceSnapshot EMPTY = new StorageInterfaceSnapshot(
        false,
        ECOStorageInterfaceMode.STORAGE,
        0L,
        0L,
        false,
        false);

    public final boolean formed;
    public final ECOStorageInterfaceMode mode;
    public final long transferredLastTick;
    public final long transferredTotal;
    public final boolean targetOnline;
    public final boolean hasController;

    private StorageInterfaceSnapshot(boolean formed, ECOStorageInterfaceMode mode, long transferredLastTick,
        long transferredTotal, boolean targetOnline, boolean hasController) {
        this.formed = formed;
        this.mode = mode;
        this.transferredLastTick = Math.max(0L, transferredLastTick);
        this.transferredTotal = Math.max(0L, transferredTotal);
        this.targetOnline = targetOnline;
        this.hasController = hasController;
    }

    public static StorageInterfaceSnapshot create(TileECOInterface storageInterface) {
        TileECOController controller = storageInterface == null ? null : storageInterface.getBoundController();
        boolean formed = controller != null && controller.isFormed();
        return storageInterface == null ? EMPTY
            : new StorageInterfaceSnapshot(
                formed,
                storageInterface.getStorageInterfaceMode(),
                storageInterface.getStorageInterfaceTransferredLastTick(),
                storageInterface.getStorageInterfaceTransferredTotal(),
                storageInterface.isNetworkOnline(),
                controller != null);
    }

    public void write(ByteBuf buffer) {
        buffer.writeBoolean(this.formed);
        buffer.writeByte(this.mode.ordinal());
        buffer.writeLong(this.transferredLastTick);
        buffer.writeLong(this.transferredTotal);
        buffer.writeBoolean(this.targetOnline);
        buffer.writeBoolean(this.hasController);
    }

    public static StorageInterfaceSnapshot read(ByteBuf buffer) {
        return new StorageInterfaceSnapshot(
            buffer.readBoolean(),
            ECOStorageInterfaceMode.byOrdinal(buffer.readUnsignedByte()),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readBoolean(),
            buffer.readBoolean());
    }
}
