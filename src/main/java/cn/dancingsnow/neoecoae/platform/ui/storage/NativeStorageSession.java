package cn.dancingsnow.neoecoae.platform.ui.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.gui.storage.StoragePanelState;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;
import cn.dancingsnow.neoecoae.platform.ui.NativeActionSyncHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Server-authored source snapshots and bounded physical-drive actions; display positions never become vanilla slots.
 */
public final class NativeStorageSession extends NativeActionSyncHandler {

    public static final int PAGE_SIZE = 33;
    private final ECOStorageSystemBlockEntity host;
    private final StorageHostUI.Config config;
    private final ServerTasks.Session serverSession = ServerTasks.captureSession();
    private StoragePanelState state = StoragePanelState.EMPTY;
    private CompoundTag previous;
    private int offset, nextToken, pendingToken;
    private boolean canExtractComponents;
    private long revision;
    private static final int BOOKMARK_BATCH = 128;
    private int bookmarkSequence;
    private volatile int readyBookmarkSequence = -1;
    private final List<ItemStack> bookmarkInventory = new ArrayList<>();

    public NativeStorageSession(ECOStorageSystemBlockEntity host, StorageHostUI.Config config) {
        this.host = host;
        this.config = config;
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
        } else if (id == 3) {
            int sequence = buffer.readInt();
            boolean last = buffer.readBoolean();
            List<ItemStack> stacks = readBookmarkStacks(buffer);
            synchronized (bookmarkInventory) {
                if (sequence == bookmarkSequence) {
                    bookmarkInventory.addAll(stacks);
                    if (last) readyBookmarkSequence = sequence;
                }
            }
        } else throw new java.io.IOException("Unknown storage response: " + id);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws java.io.IOException {
        if (id == 3) {
            int sequence = buffer.readInt();
            boolean last = buffer.readBoolean();
            List<ItemStack> candidates = readBookmarkStacks(buffer);
            if (buffer.isReadable()) throw new java.io.IOException("Trailing bookmark query bytes");
            serverSession.execute(() -> {
                if (!validSession()) return;
                List<ItemStack> available = new ArrayList<>();
                var grid = bookmarkGrid();
                if (grid != null) {
                    appeng.api.networking.storage.IStorageGrid storage = grid
                        .getCache(appeng.api.networking.storage.IStorageGrid.class);
                    var monitor = storage.getItemInventory();
                    for (ItemStack sample : candidates) {
                        var found = monitor.getStorageList()
                            .findPrecise(appeng.util.item.AEItemStack.create(sample));
                        if (found != null && found.getStackSize() > 0) available.add(
                            ItemStacks.copyWithCount(sample, (int) Math.min(Integer.MAX_VALUE, found.getStackSize())));
                    }
                }
                syncToClient(3, output -> {
                    output.writeInt(sequence);
                    output.writeBoolean(last);
                    writeBookmarkStacks(output, available);
                });
            });
            return;
        }
        if (id == 4) {
            List<ItemStack> requested = readBookmarkStacks(buffer);
            if (buffer.isReadable()) throw new java.io.IOException("Trailing bookmark extraction bytes");
            serverSession.execute(() -> { if (validSession()) pullBookmarks(requested); });
            return;
        }
        if (id == 0) {
            int requested = buffer.readInt();
            serverSession.execute(() -> { if (validSession()) offset = Math.max(0, requested); });
            return;
        }
        if (id == 2) {
            int slot = buffer.readInt();
            if (buffer.isReadable()) throw new java.io.IOException("Trailing storage quick-move bytes");
            serverSession.execute(() -> { if (validSession()) quickMoveFromInventory(slot); });
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

    private appeng.api.networking.IGrid bookmarkGrid() {
        if (!host.isFormed() || !host.getMainNode()
            .isOnline()) return null;
        var view = host.getMainNode()
            .getGrid();
        if (view == null) return null;
        var grid = view.nativeGrid();
        appeng.api.networking.security.ISecurityGrid security = grid
            .getCache(appeng.api.networking.security.ISecurityGrid.class);
        return security != null
            && security.hasPermission(getSyncManager().getPlayer(), appeng.api.config.SecurityPermissions.EXTRACT)
                ? grid
                : null;
    }

    private List<ItemStack> readBookmarkStacks(PacketBuffer buffer) throws java.io.IOException {
        int count = buffer.readInt();
        if (count < 0 || count > BOOKMARK_BATCH) throw new java.io.IOException("Invalid bookmark batch size");
        List<ItemStack> stacks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemStack stack = ItemStacks.parseOptional(config.registries(), bytes(buffer).readNbt());
            if (ItemStacks.isEmpty(stack)) throw new java.io.IOException("Invalid bookmark stack");
            stacks.add(stack);
        }
        return stacks;
    }

    private void writeBookmarkStacks(PacketBuffer buffer, List<ItemStack> stacks) {
        buffer.writeInt(stacks.size());
        for (ItemStack stack : stacks) bytes(buffer).writeNbt(ItemStacks.save(config.registries(), stack));
    }

    /** Query only keys used by the bookmark recipe chain, in bounded packets, rather than exporting the network. */
    @SideOnly(Side.CLIENT)
    public int queryBookmarkInventory(List<ItemStack> candidates) {
        final int sequence;
        synchronized (bookmarkInventory) {
            sequence = ++bookmarkSequence;
            readyBookmarkSequence = -1;
            bookmarkInventory.clear();
        }
        java.util.Map<cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey, ItemStack> unique = new java.util.LinkedHashMap<>();
        for (ItemStack candidate : candidates) if (!ItemStacks.isEmpty(candidate)) unique.putIfAbsent(
            cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey.of(candidate),
            ItemStacks.copyWithCount(candidate, 1));
        List<ItemStack> keys = new ArrayList<>(unique.values());
        for (int offset = 0; offset < Math.max(1, keys.size()); offset += BOOKMARK_BATCH) {
            int end = Math.min(keys.size(), offset + BOOKMARK_BATCH);
            List<ItemStack> batch = new ArrayList<>(keys.subList(offset, end));
            boolean last = end == keys.size();
            syncToServer(3, output -> {
                output.writeInt(sequence);
                output.writeBoolean(last);
                writeBookmarkStacks(output, batch);
            });
        }
        return sequence;
    }

    @SideOnly(Side.CLIENT)
    public boolean bookmarkInventoryReady(int sequence) {
        return readyBookmarkSequence == sequence;
    }

    @SideOnly(Side.CLIENT)
    public List<ItemStack> bookmarkInventory() {
        synchronized (bookmarkInventory) {
            List<ItemStack> result = new ArrayList<>(bookmarkInventory.size());
            for (ItemStack stack : bookmarkInventory) result.add(stack.copy());
            return result;
        }
    }

    @SideOnly(Side.CLIENT)
    public void requestBookmarks(List<ItemStack> items) {
        for (int offset = 0; offset < items.size(); offset += BOOKMARK_BATCH) {
            List<ItemStack> batch = new ArrayList<>(
                items.subList(offset, Math.min(items.size(), offset + BOOKMARK_BATCH)));
            syncToServer(4, output -> writeBookmarkStacks(output, batch));
        }
    }

    private void pullBookmarks(List<ItemStack> requested) {
        var grid = bookmarkGrid();
        if (grid == null) return;
        var player = getSyncManager().getPlayer();
        appeng.api.networking.storage.IStorageGrid storage = grid
            .getCache(appeng.api.networking.storage.IStorageGrid.class);
        appeng.api.networking.energy.IEnergyGrid energy = grid.getCache(appeng.api.networking.energy.IEnergyGrid.class);
        var monitor = storage.getItemInventory();
        var source = new appeng.api.networking.security.PlayerSource(player, host);
        var inventory = appeng.util.InventoryAdaptor
            .getAdaptor(player, net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
        for (ItemStack wanted : requested) {
            // A player inventory bounds useful extraction even if a forged request carries an enormous count.
            int capacity = (int) Math.min(
                Integer.MAX_VALUE,
                (long) player.inventory.mainInventory.length * Math.max(1, wanted.getMaxStackSize()));
            ItemStack sample = ItemStacks.copyWithCount(wanted, Math.min(wanted.stackSize, capacity));
            ItemStack overflow = inventory.simulateAdd(sample);
            int amount = sample.stackSize - ItemStacks.count(overflow);
            if (amount <= 0) continue;
            var extracted = appeng.util.Platform.poweredExtraction(
                energy,
                monitor,
                appeng.util.item.AEItemStack.create(ItemStacks.copyWithCount(sample, amount)),
                source);
            if (extracted == null) continue;
            ItemStack leftover = inventory.addItems(extracted.getItemStack());
            if (!ItemStacks.isEmpty(leftover)) {
                var rejected = monitor.injectItems(
                    appeng.util.item.AEItemStack.create(leftover),
                    appeng.api.config.Actionable.MODULATE,
                    source);
                if (rejected != null) appeng.util.Platform.addToPlayerInvOrDrop(player, rejected.getItemStack());
            }
        }
        player.inventory.markDirty();
        cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
    }

    @SideOnly(Side.CLIENT)
    public void quickMove(int slot) {
        syncToServer(2, output -> output.writeInt(slot));
    }

    private void quickMoveFromInventory(int slot) {
        var player = getSyncManager().getPlayer();
        if (slot < 0 || slot >= player.inventory.mainInventory.length
            || !ItemStacks.isEmpty(player.inventory.getItemStack())) return;
        ItemStack stack = player.inventory.getStackInSlot(slot);
        if (ItemStacks.isEmpty(stack)) return;
        if (cn.dancingsnow.neoecoae.platform.registry.StorageTags.isInfiniteComponent(stack)) {
            ItemStack remaining = config.infiniteComponentInventory()
                .insertItem(0, stack.copy(), false);
            if (ItemStacks.count(remaining) == ItemStacks.count(stack)) return;
            player.inventory.setInventorySlotContents(slot, ItemStacks.isEmpty(remaining) ? null : remaining);
            player.inventory.markDirty();
            cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
        } else {
            cn.dancingsnow.neoecoae.blocks.entity.storage.StorageCellUiInsertion.quickMove(host, player, slot);
        }
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
