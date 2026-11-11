package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.fluid.FluidStack;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import cn.dancingsnow.neoecoae.platform.ui.NativeTileSession;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Exact quantities and sample-only configuration, guarded by the hatch's live server menu. */
public final class NativeFluidHatchSession extends NativeTileSession {

    private final ECOFluidInputHatchBlockEntity host;
    private CompoundTag state = new CompoundTag();
    private CompoundTag previous;

    public NativeFluidHatchSession(ECOFluidInputHatchBlockEntity host) {
        super(host);
        this.host = host;
    }

    public boolean cardInstalled() {
        return state.getBoolean("card");
    }

    public long target() {
        return state.getLong("target");
    }

    public long stored() {
        return state.getLong("stored");
    }

    public AEFluidKey selected() {
        AEKey key = AEKey.fromTagGeneric(RegistryAccess.EMPTY, state.getCompound("selected"));
        return key instanceof AEFluidKey fluid ? fluid : null;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!validSession()) return;
        state = new CompoundTag();
        state.putBoolean("card", host.hasNetworkPullCard());
        state.putLong("target", host.getTargetAmount());
        state.putLong("stored", host.getStoredFluidAmount());
        AEFluidKey key = AEFluidKey.of(host.getMarkedFluid());
        if (key != null) state.put("selected", key.toTagGeneric(RegistryAccess.EMPTY));
        if (init || !state.equals(previous)) {
            previous = state.copy();
            syncToClient(0, buffer -> new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).writeNbt(state));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Unknown fluid hatch response: " + id);
        state = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        Runnable action;
        if (id == 0) {
            CompoundTag tag = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY).readNbt();
            if (tag == null) return;
            AEKey key = tag.isEmpty() ? null : AEKey.fromTagGeneric(RegistryAccess.EMPTY, tag);
            if (!tag.isEmpty() && !(key instanceof AEFluidKey)) return;
            action = () -> host
                .setMarkedFluid(key == null ? FluidStack.EMPTY : FluidStack.fromNative(((AEFluidKey) key).toStack(1)));
        } else if (id == 1) {
            long amount = buffer.readLong();
            if (amount < 0) return;
            action = () -> host.setTargetAmount(amount);
        } else throw new IOException("Unknown fluid hatch action: " + id);
        if (buffer.isReadable()) throw new IOException("Trailing fluid hatch action bytes");
        edit(() -> { if (host.hasNetworkPullCard()) action.run(); });
    }

    @SideOnly(Side.CLIENT)
    public void select(AEFluidKey fluid) {
        syncToServer(
            0,
            buffer -> new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY)
                .writeNbt(fluid == null ? new CompoundTag() : fluid.toTagGeneric(RegistryAccess.EMPTY)));
    }

    public void setTarget(String text) {
        try {
            long amount = Long.parseLong(text);
            if (amount >= 0) sendTarget(amount);
        } catch (NumberFormatException ignored) {
            // Keep the previous exact target while the field is empty or outside the long range.
        }
    }

    @SideOnly(Side.CLIENT)
    private void sendTarget(long amount) {
        syncToServer(1, buffer -> buffer.writeLong(amount));
    }
}
