package cn.dancingsnow.neoecoae.platform.ui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeSnapshotSession;

/** Actual controller values and worker output previews; display cells never expose owned outputs as slots. */
public final class NativeCraftingSession extends NativeSnapshotSession {

    private final ECOCraftingSystemBlockEntity host;
    private int offset;

    public NativeCraftingSession(ECOCraftingSystemBlockEntity host) {
        super(host);
        this.host = host;
    }

    @Override
    protected CompoundTag capture() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", host.isFormed());
        tag.putInt(
            "tier",
            host.getTier()
                .getTier());
        tag.putString(
            "tierName",
            host.getTier()
                .getPanelTierName()
                .getString());
        tag.putInt("frequency", host.getNetworkFrequency());
        tag.putBoolean("overclock", host.isOverclocked());
        tag.putBoolean("cooling", host.isActiveCooling());
        tag.putInt("overclockTimes", host.getOverclockTimes());
        tag.putInt("effectiveOverclock", host.getEffectiveOverclockTimes());
        tag.putLong("coolant", host.getDisplayedCoolantAmount());
        tag.putLong("coolantCapacity", host.getDisplayedCoolantCapacity());
        tag.putLong("averageNanos", host.getPerformanceAverageNanos());
        ListTag outputs = new ListTag();
        var cluster = host.getCluster();
        int index = 0, busy = 0, capacity = 0;
        if (cluster != null && !cluster.isDestroyed()) {
            tag.putInt(
                "workers",
                cluster.getWorkers()
                    .size());
            tag.putInt(
                "parallelCores",
                cluster.getParallelCores()
                    .size());
            tag.putInt(
                "patternBuses",
                cluster.getPatternBuses()
                    .size());
            tag.putInt("multiplier", cluster.getNetworkMultiplier());
            for (var worker : cluster.getWorkers()) for (var thread : worker.getCraftingThreads()) {
                capacity++;
                if (!thread.isBusy()) continue;
                busy++;
                if (index++ < offset || outputs.size() >= 6) continue;
                var snapshot = thread.createSnapshot();
                CompoundTag row = new CompoundTag();
                row.put("item", ItemStacks.save(RegistryAccess.EMPTY, thread.getOutputItem()));
                row.putString(
                    "amount",
                    thread.getExactOutputAmount()
                        .toString());
                row.putInt("progress", snapshot.progress());
                row.putInt("maxProgress", snapshot.maxProgress());
                row.putString(
                    "crafts",
                    thread.getExactCraftCount()
                        .toString());
                outputs.add(row);
            }
        }
        tag.putInt("busy", busy);
        tag.putInt("capacity", capacity);
        tag.putInt("offset", offset);
        tag.put("outputs", outputs);
        return tag;
    }

    @Override
    protected void apply(int action, int argument) {
        switch (action) {
            case 0:
                host.setOverclocked(!host.isOverclocked());
                break;
            case 1:
                host.setActiveCooling(!host.isActiveCooling());
                break;
            case 3:
                offset = Math.max(0, Math.min(65530, argument));
                break;
            case 4:
                host.adjustNetworkFrequency(Integer.signum(argument));
                break;
            default:
                throw new IllegalArgumentException("Unknown crafting page action: " + action);
        }
        host.saveChanges();
    }

    public CompoundTag output(int index) {
        ListTag outputs = state().getList("outputs", 10);
        return index >= 0 && index < outputs.size() ? outputs.getCompound(index) : new CompoundTag();
    }
}
