package cn.dancingsnow.neoecoae.gui;

import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import cn.dancingsnow.neoecoae.network.HostUiStatePacket;
import cn.dancingsnow.neoecoae.network.NENetwork;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public abstract class HostUiStateContainer extends Container {

    private byte[] lastSentPayload;
    private long sentRevision;
    private long receivedRevision = -1L;
    private int syncTicks;

    @Override
    public void addCraftingToCrafters(net.minecraft.inventory.ICrafting listener) {
        super.addCraftingToCrafters(listener);
        if (listener instanceof EntityPlayerMP) {
            this.sendState((EntityPlayerMP) listener);
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.syncTicks++;
        int interval = Math.max(1, this.hostUiSyncIntervalTicks());
        if (this.lastSentPayload != null && this.syncTicks % interval != 0) {
            return;
        }
        byte[] payload = this.encodeState();
        if (Arrays.equals(this.lastSentPayload, payload)) {
            return;
        }
        this.lastSentPayload = payload;
        this.sentRevision++;
        HostUiStatePacket packet = new HostUiStatePacket(this.windowId, this.sentRevision, payload);
        for (Object listener : this.crafters) {
            if (listener instanceof EntityPlayerMP) {
                NENetwork.CHANNEL.sendTo(packet, (EntityPlayerMP) listener);
            }
        }
    }

    public final void applyHostUiState(long revision, byte[] payload) {
        if (revision <= this.receivedRevision || payload == null) {
            return;
        }
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        try {
            this.readHostUiState(buffer);
            this.receivedRevision = revision;
        } finally {
            buffer.release();
        }
    }

    protected abstract void writeHostUiState(ByteBuf buffer);

    protected abstract void readHostUiState(ByteBuf buffer);

    protected int hostUiSyncIntervalTicks() {
        return 10;
    }

    private void sendState(EntityPlayerMP player) {
        byte[] payload = this.encodeState();
        this.lastSentPayload = payload;
        this.sentRevision++;
        NENetwork.CHANNEL.sendTo(new HostUiStatePacket(this.windowId, this.sentRevision, payload), player);
    }

    private byte[] encodeState() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            this.writeHostUiState(buffer);
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), payload);
            return payload;
        } finally {
            buffer.release();
        }
    }
}
