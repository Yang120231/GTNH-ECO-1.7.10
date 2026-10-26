package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingRequester;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Native link/nexus ownership; item and fluid delivery remains through the real AE2 requester. */
public final class CraftingLink implements ICraftingLink {

    private final appeng.crafting.CraftingLink link;
    private final UUID id;

    public CraftingLink(CompoundTag data, ICraftingCPU cpu) {
        id = id(data);
        link = new appeng.crafting.CraftingLink(nativeData(data, id), cpu);
        if (link.isStandalone()) link.setNexus(new appeng.crafting.CraftingLinkNexus(id.toString()));
    }

    public CraftingLink(CompoundTag data, ICraftingRequester requester) {
        id = id(data);
        link = new appeng.crafting.CraftingLink(nativeData(data, id), requester);
    }

    private static UUID id(CompoundTag data) {
        return data.hasUUID("craftId") ? data.getUUID("craftId") : UUID.fromString(data.getString("CraftID"));
    }

    private static net.minecraft.nbt.NBTTagCompound nativeData(CompoundTag source, UUID id) {
        CompoundTag data = source.copy();
        data.putString("CraftID", id.toString());
        return data.nativeTag();
    }

    public static CompoundTag generateData(UUID id, boolean standalone, boolean requester) {
        CompoundTag data = new CompoundTag();
        data.putUUID("craftId", id);
        data.putBoolean("standalone", standalone);
        data.putBoolean("req", requester);
        return data;
    }

    @Override
    public UUID getCraftingID() {
        return id;
    }

    @Override
    public boolean isStandalone() {
        return link.isStandalone();
    }

    @Override
    public boolean isCanceled() {
        return link.isCanceled();
    }

    @Override
    public boolean isDone() {
        return link.isDone();
    }

    @Override
    public void cancel() {
        link.cancel();
    }

    public void markDone() {
        link.markDone();
    }

    public long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0) return 0;
        var offered = key.toNative(amount);
        var remainder = link.injectItems(offered, mode);
        return remainder == null ? amount : Math.max(0, Math.min(amount, amount - remainder.getStackSize()));
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        link.writeToNBT(data.nativeTag());
        data.putUUID("craftId", id);
    }

    @Override
    public appeng.crafting.CraftingLink nativeLink() {
        return link;
    }
}
