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
        tag.putBoolean("modeBlocked", host.isModeSwitchBlockedNoticeActive());
        var settings = cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings.of(
            host.getMainNode()
                .getGrid());
        tag.putBoolean(
            "substitutionsIgnored",
            settings != null && settings.neoecoae$hasComputationHost()
                ? settings.neoecoae$isIgnoringPatternSubstitutions()
                : host.isLocallyIgnoringPatternSubstitutions());
        tag.putInt("substitutionPatterns", settings == null ? 0 : settings.neoecoae$getSubstitutionPatternCount());
        ListTag maintenance = new ListTag();
        for (var line : host.getMaintenanceStatusLines())
            maintenance.add(cn.dancingsnow.neoecoae.platform.nbt.StringTag.valueOf(line.toJson()));
        tag.put("maintenance", maintenance);
        tag.putInt("overclockTimes", host.getOverclockTimes());
        tag.putInt("effectiveOverclock", host.getEffectiveOverclockTimes());
        tag.putLong("coolant", host.getDisplayedCoolantAmount());
        tag.putLong("coolantCapacity", host.getDisplayedCoolantCapacity());
        tag.putLong("averageNanos", host.getPerformanceAverageNanos());
        var capability = host.getCapabilitySnapshot();
        tag.putBoolean(
            "connected",
            host.getMainNode()
                .isOnline());
        tag.putBoolean("virtual", capability.virtualMode());
        tag.putLong(
            "batchCapacity",
            capability.totalBatchCapacity()
                .unlimited() ? Long.MAX_VALUE
                    : capability.totalBatchCapacity()
                        .finiteValue());
        tag.putLong("parallel", capability.ftParallelCapacity());
        tag.putLong("overflow", capability.overflowCapacity());
        tag.putInt("activeWorkers", capability.activeFxCount());
        tag.putInt("physicalWorkers", capability.physicalFxCount());
        tag.putInt("normalSwitches", capability.normalSwitchHosts());
        tag.putInt("highSwitches", capability.highEnergySwitchHosts());
        tag.putInt("multiplier", Math.max(1, capability.networkMultiplier()));
        tag.putInt(
            "executionTicks",
            capability.virtualMode() ? 1
                : (int) Math.ceil(10.0 / (Math.min(9, host.getEffectiveOverclockTimes()) + 1)));
        tag.putInt("coolantOverclock", host.getDisplayedCoolingMaxOverclock());
        var coolant = host.getDisplayedCoolantFluid()
            .toNative();
        var output = host.getDisplayedOutputFluid()
            .toNative();
        tag.put(
            "coolantFluid",
            new CompoundTag(
                coolant == null ? new net.minecraft.nbt.NBTTagCompound()
                    : coolant.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        tag.put(
            "outputFluid",
            new CompoundTag(
                output == null ? new net.minecraft.nbt.NBTTagCompound()
                    : output.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        tag.putInt("outputCapacity", host.getDisplayedOutputCapacity());
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
        captureArtwork(tag);
        return tag;
    }

    private void captureArtwork(CompoundTag tag) {
        var cluster = host.getCluster();
        var unformed = cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState.UNFORMED;
        if (!host.isFormed() || cluster == null || cluster.isDestroyed()) {
            tag.put("artwork", unformed.toTag());
            return;
        }
        var workers = new java.util.ArrayList<>(cluster.getWorkers());
        workers.sort(
            java.util.Comparator.comparingDouble(
                worker -> worker.getBlockPos()
                    .distSqr(host.getBlockPos())));
        var upper = new java.util.ArrayList<Integer>();
        var lower = new java.util.ArrayList<Integer>();
        var cores = new java.util.HashMap<cn.dancingsnow.neoecoae.platform.minecraft.BlockPos, Integer>();
        for (var core : cluster.getParallelCores()) cores.put(
            core.getBlockPos(),
            core.getTier()
                .getTier());
        for (var worker : workers) {
            upper.add(
                cores.getOrDefault(
                    worker.getBlockPos()
                        .above(),
                    0));
            lower.add(
                cores.getOrDefault(
                    worker.getBlockPos()
                        .below(),
                    0));
        }
        boolean mirrored = host.getBlockState()
            .getValue(cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.MIRRORED);
        var displayed = cluster.getNetworkCluster() == null ? workers
            : cluster.getNetworkCluster()
                .collectCandidateWorkers();
        long queued = 0;
        for (var worker : displayed)
            queued = cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingAdd(queued, worker.getQueuedCraftCount());
        tag.putLong("queued", queued);
        var capability = host.getCapabilitySnapshot();
        var panel = cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState.of(
            host.getTier()
                .getTier(),
            upper,
            lower,
            workers.size(),
            mirrored,
            host.isActiveCooling() && !host.getDisplayedCoolantFluid()
                .isEmpty() && host.getDisplayedCoolantAmount() > 0,
            host.hasNetworkSwitch(),
            capability.totalBatchCapacity()
                .unlimited()
                    ? 1000
                    : cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState.permille(
                        queued,
                        capability.totalBatchCapacity()
                            .finiteValue()),
            cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState
                .permille(capability.activeFxCount(), capability.physicalFxCount()));
        tag.put("artwork", panel.toTag());
        ListTag cells = new ListTag();
        CompoundTag[] entries = new CompoundTag[11];
        for (int index = 0; index < 11; index++) entries[index] = new CompoundTag();
        var previews = new java.util.ArrayList<Preview>();
        for (var worker : displayed) {
            Preview preview = new Preview(null, java.math.BigInteger.ZERO);
            for (var thread : worker.getCraftingThreads()) {
                var key = thread.getDisplayedOutputKey();
                if (key == null) continue;
                java.math.BigInteger amount = java.math.BigInteger.ZERO;
                for (var current : worker.getCraftingThreads())
                    amount = amount.add(current.getDisplayedItemAmount(key));
                if (amount.signum() <= 0) continue;
                preview = new Preview(key, amount);
                break;
            }
            previews.add(preview);
        }
        if (cluster.getNetworkCluster() != null) {
            var merged = new java.util.LinkedHashMap<cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey, java.math.BigInteger>();
            for (var preview : previews)
                if (preview.key() != null) merged.merge(preview.key(), preview.amount(), java.math.BigInteger::add);
            previews.clear();
            merged.forEach((key, amount) -> previews.add(new Preview(key, amount)));
        }
        for (int slot = 0; slot < Math.min(11, previews.size()); slot++) {
            var preview = previews.get(slot);
            if (preview.key() == null) continue;
            var row = entries[cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerPanelState.cellOf(slot, mirrored)];
            row.put(
                "item",
                ItemStacks.save(
                    RegistryAccess.EMPTY,
                    preview.key()
                        .toStack(1)));
            row.putString(
                "amount",
                preview.amount()
                    .toString());
        }
        for (var entry : entries) cells.add(entry);
        tag.put("displayCells", cells);
    }

    @com.github.bsideup.jabel.Desugar
    private record Preview(cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey key, java.math.BigInteger amount) {}

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
            case 5:
                var settings = cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings.of(
                    host.getMainNode()
                        .getGrid());
                if (settings != null && settings.neoecoae$hasComputationHost()) settings
                    .neoecoae$setIgnoringPatternSubstitutions(!settings.neoecoae$isIgnoringPatternSubstitutions());
                else host.applyNetworkIgnoringPatternSubstitutions(!host.isLocallyIgnoringPatternSubstitutions());
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
