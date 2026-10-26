package cn.dancingsnow.neoecoae.platform.ui.computation;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.ui.NativeTileSession;

/** Phantom samples are configuration: they never remove or create an item in the player's inventory. */
public final class NativeFuzzyPlanningSession extends NativeTileSession {

    private final ECOMachineInterfaceBlockEntity<?> host;
    private CompoundTag state = new CompoundTag(), previous;

    public NativeFuzzyPlanningSession(ECOMachineInterfaceBlockEntity<?> host) {
        super(host);
        this.host = host;
    }

    public ItemStack sample(int slot) {
        return ItemStacks.parseOptional(RegistryAccess.EMPTY, state.getCompound(Integer.toString(slot)));
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        state = new CompoundTag();
        for (int slot = 0; slot < 63; slot++) {
            var sample = host.getFuzzyPlanningInventory().getStackInSlot(slot);
            if (!ItemStacks.isEmpty(sample)) state.put(Integer.toString(slot), ItemStacks.save(RegistryAccess.EMPTY, sample));
        }
        if (init || !state.equals(previous)) {
            previous = state.copy();
            CompoundTag snapshot = state;
            syncToClient(0, buffer -> new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).writeNbt(snapshot));
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Invalid fuzzy filter snapshot");
        state = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
        if (state == null || buffer.isReadable()) throw new IOException("Malformed fuzzy filter snapshot");
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Invalid fuzzy filter action");
        int slot = buffer.readInt();
        CompoundTag encoded = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
        if (slot < 0 || slot >= 63 || encoded == null || buffer.isReadable()) throw new IOException("Malformed fuzzy filter action");
        ItemStack sample = ItemStacks.parseOptional(RegistryAccess.EMPTY, encoded);
        if (!encoded.isEmpty() && ItemStacks.isEmpty(sample)) return;
        ItemStack owned = ItemStacks.copyWithCount(sample, 1);
        edit(() -> host.getFuzzyPlanningInventory().setStackInSlot(slot, owned));
    }

    public void setSample(int slot, ItemStack sample) {
        CompoundTag encoded = ItemStacks.save(RegistryAccess.EMPTY, ItemStacks.copyWithCount(sample, 1));
        syncToServer(0, buffer -> {
            buffer.writeInt(slot);
            new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).writeNbt(encoded);
        });
    }
}
