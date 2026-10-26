package cn.dancingsnow.neoecoae.platform.ui.storage;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.StorageInterfaceFilter;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.ui.NativeTileSession;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Persisted interface settings and exact key samples, authored by the real server-side interface. */
public final class NativeStorageInterfaceSession extends NativeTileSession {

    private final ECOMachineInterfaceBlockEntity<?> host;
    private CompoundTag state = new CompoundTag();
    private CompoundTag previous;
    private long sampledTransfer, nextSample;
    private static volatile NativeStorageInterfaceSession inspection;

    public NativeStorageInterfaceSession(ECOMachineInterfaceBlockEntity<?> host) {
        super(host);
        this.host = host;
        if ((Boolean.getBoolean("neoecoae.storageUiAcceptance")
            || Boolean.getBoolean("neoecoae.storageUiNetworkAcceptance")) && host.getWorldObj().isRemote)
            inspection = this;
    }

    static NativeStorageInterfaceSession acceptanceInspection() {
        return inspection;
    }

    public ECOStorageInterfaceMode mode() {
        return ECOStorageInterfaceMode.values()[Math.max(0, Math.min(2, state.getInt("mode")))];
    }

    public boolean ignoringCreative() {
        return state.getBoolean("ignoreCreative");
    }

    public boolean excludingStorageBus() {
        return state.getBoolean("excludeBus");
    }

    public boolean infiniteReady() {
        return state.getBoolean("infinite");
    }

    public boolean online() {
        return state.getBoolean("online");
    }

    public long transferred() {
        return state.getLong("transfer");
    }

    public AEKey filter(int slot) {
        return StorageInterfaceFilter.get(state.getCompound("filters"), slot, RegistryAccess.EMPTY);
    }

    public boolean unavailable(int slot) {
        return state.getCompound("filters")
            .contains(Integer.toString(slot)) && filter(slot) == null;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        long tick = host.getWorldObj()
            .getTotalWorldTime();
        if (init || tick >= nextSample) {
            sampledTransfer = host.getTransferredLastTick();
            nextSample = tick + 5;
        }
        state = new CompoundTag();
        state.putInt(
            "mode",
            host.getStorageInterfaceMode()
                .ordinal());
        state.putBoolean("ignoreCreative", host.isIgnoringCreativeStorageInput());
        state.putBoolean("excludeBus", host.isExcludingStorageBusContainers());
        state.putBoolean("infinite", host.isInfiniteTransferAvailable());
        state.putBoolean("online", host.isTargetOnline());
        state.putLong("transfer", sampledTransfer);
        state.put("filters", host.getStorageResourceFilters());
        if (init || !state.equals(previous)) {
            previous = state.copy();
            syncToClient(0, buffer -> new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).writeNbt(state));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Unknown storage interface response: " + id);
        state = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        Runnable action;
        if (id == 0) {
            int mode = buffer.readInt();
            if (mode < 0 || mode >= ECOStorageInterfaceMode.values().length) return;
            action = () -> {
                if (host.supportsStorageInterfaceUi())
                    host.setStorageInterfaceMode(ECOStorageInterfaceMode.values()[mode]);
            };
        } else if (id == 1) action = host::toggleIgnoreCreativeStorageInput;
        else if (id == 2) action = host::toggleExcludeStorageBusContainers;
        else if (id == 3) {
            int slot = buffer.readInt();
            CompoundTag encoded = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
            if (slot < 0 || slot >= StorageInterfaceFilter.SLOTS || encoded == null) return;
            AEKey key = encoded.isEmpty() ? null : StorageInterfaceFilter.decode(encoded, RegistryAccess.EMPTY);
            if (!encoded.isEmpty() && key == null) return;
            action = () -> host.setStorageResourceFilter(slot, key);
        } else throw new IOException("Unknown storage interface action: " + id);
        if (buffer.isReadable()) throw new IOException("Trailing storage interface action bytes");
        edit(action);
    }

    @SideOnly(Side.CLIENT)
    public void selectMode(ECOStorageInterfaceMode mode) {
        syncToServer(0, buffer -> buffer.writeInt(mode.ordinal()));
    }

    @SideOnly(Side.CLIENT)
    public void toggleCreative() {
        syncToServer(1);
    }

    @SideOnly(Side.CLIENT)
    public void toggleStorageBus() {
        syncToServer(2);
    }

    @SideOnly(Side.CLIENT)
    public void setFilter(int slot, AEKey key) {
        syncToServer(3, buffer -> {
            buffer.writeInt(slot);
            new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY)
                .writeNbt(key == null ? new CompoundTag() : StorageInterfaceFilter.encode(key, RegistryAccess.EMPTY));
        });
    }
}
