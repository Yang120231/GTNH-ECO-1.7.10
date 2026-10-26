package cn.dancingsnow.neoecoae.platform.ui.storage;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.gui.storage.StoragePanelState;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Server-authored source snapshots and bounded physical-drive actions; display positions never become vanilla slots.
 */
public final class NativeStorageSession extends SyncHandler {

    public static final int PAGE_SIZE = 33;
    private final ECOStorageSystemBlockEntity host;
    private final StorageHostUI.Config config;
    private final ServerTasks.Session serverSession = ServerTasks.captureSession();
    private StoragePanelState state = StoragePanelState.EMPTY;
    private CompoundTag previous;
    private int offset, nextToken, pendingToken;
    private boolean canExtractComponents;
    private long revision;
    private static volatile NativeStorageSession inspection;

    public static NativeStorageSession acceptanceInspection() {
        return inspection;
    }

    public NativeStorageSession(ECOStorageSystemBlockEntity host, StorageHostUI.Config config) {
        this.host = host;
        this.config = config;
        if ((Boolean.getBoolean("neoecoae.clientAcceptance") || Boolean.getBoolean("neoecoae.storageUiAcceptance")
            || Boolean.getBoolean("neoecoae.storageUiNetworkAcceptance")) && host.getWorldObj().isRemote)
            inspection = this;
    }

    public StoragePanelState state() {
        return state;
    }

    public long revision() {
        return revision;
    }

    public boolean canExtractComponents() {
        return canExtractComponents;
    }

    private RegistryFriendlyByteBuf bytes(PacketBuffer buffer) {
        return new RegistryFriendlyByteBuf(buffer, config.registries());
    }

    private boolean validSession() {
        return isValid() && cn.dancingsnow.neoecoae.platform.ui.NativeUiAccess
            .canEdit(host, getSyncManager().getPlayer(), getSyncManager().getContainer());
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        state = StoragePanelState.capture(config, offset, PAGE_SIZE);
        offset = state.cellOffset();
        canExtractComponents = config.canExtractInfiniteComponents()
            .getAsBoolean();
        CompoundTag tag = state.toTag(config.registries());
        tag.putBoolean("canExtractComponents", canExtractComponents);
        if (init || !tag.equals(previous)) {
            previous = tag.copy();
            syncToClient(0, buffer -> bytes(buffer).writeNbt(tag));
            revision++;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws java.io.IOException {
        if (id == 0) {
            CompoundTag tag = bytes(buffer).readNbt();
            state = StoragePanelState.fromTag(tag, config.registries());
            canExtractComponents = tag.getBoolean("canExtractComponents");
            revision++;
        } else if (id == 1) {
            int token = buffer.readInt();
            buffer.readBoolean();
            if (token == pendingToken) pendingToken = 0;
        } else throw new java.io.IOException("Unknown storage response: " + id);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws java.io.IOException {
        if (id == 0) {
            int requested = buffer.readInt();
            serverSession.execute(() -> { if (validSession()) offset = Math.max(0, requested); });
            return;
        }
        if (id != 1) throw new java.io.IOException("Unknown storage action: " + id);
        int token = buffer.readInt();
        boolean insert = buffer.readBoolean();
        BlockPos position = BlockPos.of(buffer.readLong());
        boolean shift = buffer.readBoolean();
        UUID expectedId = buffer.readBoolean() ? new UUID(buffer.readLong(), buffer.readLong()) : null;
        ItemStack expected = insert ? ItemStacks.parseOptional(config.registries(), bytes(buffer).readNbt()) : null;
        if (buffer.isReadable()) throw new java.io.IOException("Trailing storage action bytes");
        serverSession.execute(() -> {
            boolean accepted = false;
            try {
                if (validSession()) accepted = insert ? config.cellInserter()
                    .insert(position, expected)
                    : expectedId != null && config.cellExtractor()
                        .extract(position, expectedId, shift);
            } finally {
                if (isValid()) {
                    final boolean result = accepted;
                    syncToClient(1, output -> {
                        output.writeInt(token);
                        output.writeBoolean(result);
                    });
                }
            }
        });
    }

    @SideOnly(Side.CLIENT)
    public void scroll(int delta) {
        int requested = StoragePanelState
            .pageOffset(state.cellOffset() + delta * PAGE_SIZE, state.cellCount(), PAGE_SIZE);
        syncToServer(0, output -> output.writeInt(requested));
    }

    @SideOnly(Side.CLIENT)
    public boolean click(int index, boolean shift) {
        if (pendingToken != 0 || index < 0
            || index >= state.cells()
                .size())
            return false;
        var cell = state.cells()
            .get(index);
        if (cell.position() == null) return false;
        ItemStack carried = getSyncManager().getPlayer().inventory.getItemStack();
        boolean insert = !ItemStacks.isEmpty(carried);
        if (insert ? cell.installed() : !cell.installed() || cell.interactionId() == null) return false;
        pendingToken = ++nextToken;
        syncToServer(1, output -> {
            output.writeInt(pendingToken);
            output.writeBoolean(insert);
            output.writeLong(
                cell.position()
                    .asLong());
            output.writeBoolean(shift);
            output.writeBoolean(cell.interactionId() != null);
            if (cell.interactionId() != null) {
                output.writeLong(
                    cell.interactionId()
                        .getMostSignificantBits());
                output.writeLong(
                    cell.interactionId()
                        .getLeastSignificantBits());
            }
            if (insert) bytes(output).writeNbt(ItemStacks.save(config.registries(), carried));
        });
        return true;
    }
}
