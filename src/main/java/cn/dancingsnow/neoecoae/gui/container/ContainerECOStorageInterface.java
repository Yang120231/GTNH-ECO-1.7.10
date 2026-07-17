package cn.dancingsnow.neoecoae.gui.container;

import net.minecraft.entity.player.EntityPlayer;

import cn.dancingsnow.neoecoae.gui.HostUiStateContainer;
import cn.dancingsnow.neoecoae.gui.storage.StorageInterfaceSnapshot;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import io.netty.buffer.ByteBuf;

public class ContainerECOStorageInterface extends HostUiStateContainer {

    private static final int STATE_VERSION = 1;
    private final TileECOInterface storageInterface;
    private StorageInterfaceSnapshot state = StorageInterfaceSnapshot.EMPTY;

    public ContainerECOStorageInterface(TileECOInterface storageInterface) {
        this.storageInterface = storageInterface;
    }

    public TileECOInterface getStorageInterface() {
        return this.storageInterface;
    }

    public StorageInterfaceSnapshot state() {
        return this.state;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.storageInterface != null && !this.storageInterface.isInvalid()
            && this.storageInterface.getWorldObj() == player.worldObj
            && player.getDistanceSq(
                this.storageInterface.xCoord + 0.5D,
                this.storageInterface.yCoord + 0.5D,
                this.storageInterface.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    protected void writeHostUiState(ByteBuf buffer) {
        buffer.writeByte(STATE_VERSION);
        StorageInterfaceSnapshot.create(this.storageInterface)
            .write(buffer);
    }

    @Override
    protected void readHostUiState(ByteBuf buffer) {
        int version = buffer.readUnsignedByte();
        if (version != STATE_VERSION) {
            throw new IllegalArgumentException("Unsupported Storage Interface UI state version: " + version);
        }
        this.state = StorageInterfaceSnapshot.read(buffer);
    }
}
