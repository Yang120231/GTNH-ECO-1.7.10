package cn.dancingsnow.neoecoae.platform.ui.computation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.StringTag;
import cn.dancingsnow.neoecoae.platform.ui.NativeSnapshotSession;

/** Bounded task pages and exact order quantities, with stable session identities for destructive actions. */
public final class NativeComputationSession extends NativeSnapshotSession {

    public static final int PAGE_SIZE = 9;
    private final ECOComputationSystemBlockEntity host;
    private final IdentityHashMap<ECOCraftingCPU, Integer> identities = new IdentityHashMap<>();
    private final Map<Integer, ECOCraftingCPU> targets = new HashMap<>();
    private final IdentityHashMap<ECOComputationDriveBlockEntity, DriveTarget> driveIdentities = new IdentityHashMap<>();
    private final Map<Integer, DriveTarget> driveTargets = new HashMap<>();
    private int serial, driveSerial, offset, selected, itemOffset, driveOffset;
    private int visibleRows = 6;
    private int resourceSerial;
    private final Map<Integer, ResourceTarget> resourceTargets = new HashMap<>();

    public NativeComputationSession(ECOComputationSystemBlockEntity host) {
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
        tag.putBoolean("substitutions", !host.isNetworkIgnoringPatternSubstitutions());
        tag.putBoolean("cycles", host.isNetworkCyclePlanningEnabled());
        tag.putBoolean("fast", host.isNetworkFastPlannerEnabled());
        tag.putBoolean("smartDoubling", host.isNetworkSmartDoublingEnabled());
        tag.putInt("substitutionPatterns", host.neoecoae$getSubstitutionPatternCount());
        tag.putString(
            "selectionMode",
            host.getCpuSelectionMode()
                .name());
        tag.putInt("frequency", host.getNetworkFrequency());
        tag.putBoolean(
            "connected",
            host.getMainNode()
                .isOnline());
        var cluster = host.getCluster();
        boolean validCluster = cluster != null && !cluster.isDestroyed();
        var endgameBlockers = validCluster ? cluster.getEndgameBlockers() : Collections.singletonList("host_count");
        ListTag blockers = new ListTag();
        for (String blocker : endgameBlockers) blockers.add(StringTag.valueOf(blocker));
        tag.put("endgameBlockers", blockers);
        tag.putBoolean("endgameActive", endgameBlockers.isEmpty());
        tag.putInt("networkHosts", validCluster ? Math.max(1, cluster.getNetworkMemberCount()) : 1);
        ListTag cpus = new ListTag(), drives = new ListTag();
        if (validCluster) {
            tag.putLong("bytes", cluster.getPooledTotalStorage());
            tag.putLong("freeBytes", cluster.getAvailableStorage());
            tag.putInt("threads", cluster.getMaxThreads());
            tag.putInt("parallel", cluster.getPooledParallelism());
            tag.putInt("fullParallel", cluster.getPooledFullParallelism());
            tag.putInt("usedThreads", cluster.getActiveCPUCount());
            tag.putInt("multiplier", Math.max(1, cluster.getNetworkMultiplier()));
            tag.putInt("normalSwitches", cluster.getNormalSwitchHostCount());
            tag.putInt("highSwitches", cluster.getHighEnergySwitchHostCount());
            tag.put("artwork", artwork().toTag());
            tag.putString(
                "selectionMode",
                cluster.getSelectionMode()
                    .name());
            var active = displayedCpus();
            identities.keySet()
                .removeIf(cpu -> !active.contains(cpu));
            targets.entrySet()
                .removeIf(entry -> !active.contains(entry.getValue()));
            for (var cpu : active) if (!identities.containsKey(cpu)) {
                int token = ++serial;
                identities.put(cpu, token);
                targets.put(token, cpu);
            }
            active.sort(
                java.util.Comparator.<ECOCraftingCPU, Boolean>comparing(cpu -> cpu.getName() == null)
                    .thenComparing(
                        cpu -> cpu.getName() == null ? ""
                            : cpu.getName()
                                .getString())
                    .thenComparingInt(identities::get));
            if (!targets.containsKey(selected)) {
                var fallback = active.stream()
                    .filter(ECOCraftingCPU::isBusy)
                    .findFirst()
                    .orElse(active.isEmpty() ? null : active.get(0));
                selected = fallback == null ? 0 : identities.get(fallback);
                itemOffset = 0;
            }
            offset = Math.min(Math.max(0, active.size() - visibleRows), offset);
            tag.putInt("cpuCount", active.size());
            tag.putInt("offset", offset);
            for (int index = offset; index < Math.min(active.size(), offset + PAGE_SIZE); index++) {
                ECOCraftingCPU cpu = active.get(index);
                Integer token = identities.get(cpu);
                if (token == null) {
                    token = ++serial;
                    identities.put(cpu, token);
                    targets.put(token, cpu);
                }
                CompoundTag row = cpu(cpu);
                row.putInt("token", token);
                cpus.add(row);
            }
            ECOCraftingCPU chosen = targets.get(selected);
            if (chosen != null && active.contains(chosen)) {
                CompoundTag detail = cpu(chosen);
                detail.putInt("token", selected);
                detail.put("items", statusItems(chosen));
                detail.putInt("itemOffset", itemOffset);
                detail.putInt("itemCount", statusItemCount);
                tag.put("selected", detail);
            }
            var hardware = cluster.getDrives();
            driveIdentities.entrySet()
                .removeIf(entry -> !hardware.contains(entry.getKey()));
            driveTargets.entrySet()
                .removeIf(entry -> !hardware.contains(entry.getValue().drive));
            int columns = Math.max(
                cluster.getUpperDrives()
                    .size(),
                cluster.getLowerDrives()
                    .size());
            driveOffset = Math.min(Math.max(0, columns - 1) / 11 * 11, driveOffset);
            tag.putInt("driveOffset", driveOffset);
            tag.putInt("driveColumns", columns);
            for (int rowIndex = 0; rowIndex < 2; rowIndex++) for (int column = 0; column < 11; column++) {
                var rowDrives = new ArrayList<>(rowIndex == 0 ? cluster.getUpperDrives() : cluster.getLowerDrives());
                rowDrives.sort(
                    java.util.Comparator
                        .<ECOComputationDriveBlockEntity>comparingInt(
                            cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts::order)
                        .thenComparingDouble(
                            drive -> drive.getBlockPos()
                                .distSqr(host.getBlockPos())));
                CompoundTag row = new CompoundTag();
                boolean mirrored = host.getBlockState() != null && host.getBlockState()
                    .getValue(cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.MIRRORED);
                int index = mirrored ? 10 - column : column;
                if (index >= rowDrives.size()) {
                    drives.add(row);
                    continue;
                }
                var drive = rowDrives.get(index);
                DriveTarget target = driveIdentities.get(drive);
                if (target == null || !ItemStack.areItemStacksEqual(target.expected, drive.getCellStack())) {
                    if (target != null) driveTargets.remove(target.token);
                    target = new DriveTarget(++driveSerial, drive, ItemStack.copyItemStack(drive.getCellStack()));
                    driveIdentities.put(drive, target);
                    driveTargets.put(target.token, target);
                }
                row.putInt("token", target.token);
                row.putBoolean("canExtract", drive.canExtractCell());
                row.put("item", ItemStacks.save(RegistryAccess.EMPTY, drive.getCellStack()));
                row.putLong(
                    "position",
                    drive.getBlockPos()
                        .asLong());
                drives.add(row);
            }
        }
        tag.put("cpus", cpus);
        tag.put("drives", drives);
        tag.putInt("selectedToken", selected);
        return tag;
    }

    private ArrayList<ECOCraftingCPU> displayedCpus() {
        var result = new ArrayList<ECOCraftingCPU>();
        var cluster = host.getCluster();
        if (cluster == null || !host.getMainNode()
            .isOnline()) return result;
        var members = cluster.getNetworkCluster() == null ? java.util.Collections.singletonList(cluster)
            : cluster.getNetworkCluster()
                .getMembers();
        for (var member : members) {
            if (!member.isActive()) continue;
            result.addAll(member.getActiveCPUs());
            if (member.isNetworkRepresentative() && member.getActiveCPUCount() < member.getMaxThreads())
                result.add(member.getFakeCPU());
        }
        return result;
    }

    private cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState artwork() {
        var cluster = host.getCluster();
        if (!host.isFormed() || cluster == null)
            return cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState.UNFORMED;
        var cores = new ArrayList<>(cluster.getThreadingCores());
        cores.sort(
            java.util.Comparator.comparingDouble(
                core -> core.getBlockPos()
                    .distSqr(host.getBlockPos())));
        var parallel = new HashMap<cn.dancingsnow.neoecoae.platform.minecraft.BlockPos, Integer>();
        for (var core : cluster.getParallelCores()) parallel.put(
            core.getBlockPos(),
            core.getTier()
                .getTier());
        var upper = new ArrayList<Integer>();
        var middle = new ArrayList<Integer>();
        var lower = new ArrayList<Integer>();
        for (var core : cores) {
            upper.add(
                parallel.getOrDefault(
                    core.getBlockPos()
                        .above(),
                    0));
            middle.add(
                core.getTier()
                    .getTier());
            lower.add(
                parallel.getOrDefault(
                    core.getBlockPos()
                        .below(),
                    0));
        }
        return cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState.of(
            host.getTier()
                .getTier(),
            upper,
            middle,
            lower,
            host.getBlockState()
                .getValue(cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock.MIRRORED));
    }

    private int statusItemCount;

    private ListTag statusItems(ECOCraftingCPU cpu) {
        var logic = cpu.getLogic();
        var jobId = logic.getProcessingJobId();
        resourceTargets.values()
            .removeIf(target -> target.cpu != cpu || !java.util.Objects.equals(target.job, jobId));
        logic.refreshProcessingProgress();
        var resources = statusResources(cpu);
        var names = new HashMap<AEKey, String>();
        var sorted = new ArrayList<>(resources.keySet());
        for (AEKey key : sorted) names.put(key, key.getDisplayName());
        sorted.sort(java.util.Comparator.comparing(names::get));
        statusItemCount = sorted.size();
        itemOffset = Math.min(itemOffset, Math.max(0, (sorted.size() + 2) / 3 - 9) * 3);
        String[] kinds = { "stored", "pending", "active" };
        ListTag page = new ListTag();
        for (int index = itemOffset; index < Math.min(sorted.size(), itemOffset + 27); index++) {
            AEKey key = sorted.get(index);
            CompoundTag row = new CompoundTag();
            row.put("key", key.toTagGeneric(RegistryAccess.EMPTY));
            row.putString("name", names.get(key));
            java.math.BigInteger[] amounts = resources.get(key);
            for (int kind = 0; kind < 3; kind++)
                if (amounts[kind] != null) row.putString(kinds[kind], amounts[kind].toString());
            ResourceTarget target = resourceTargets.values()
                .stream()
                .filter(value -> value.key.equals(key))
                .findFirst()
                .orElse(null);
            if (target == null && jobId != null) {
                target = new ResourceTarget(++resourceSerial, cpu, jobId, key);
                resourceTargets.put(target.token, target);
            }
            if (target != null) {
                row.putInt("controlToken", target.token);
                row.putBoolean("canControl", logic.canControlProcessingStep(key));
                row.putBoolean("stepStopped", logic.isProcessingStepStopped(key));
                var progress = logic.getProcessingProgress(key);
                target.detailPage = Math.min(target.detailPage, progress.pages() - 1);
                row.put(
                    "processing",
                    progress.page(target.detailPage)
                        .write());
            }
            page.add(row);
        }
        return page;
    }

    /**
     * Stored, pending and active amounts of every resource of one CPU, gathered with a single item walk. Exact
     * previews win over the long counters, which only fill keys the previews do not cover.
     */
    static java.util.LinkedHashMap<AEKey, java.math.BigInteger[]> statusResources(ECOCraftingCPU cpu) {
        var logic = cpu.getLogic();
        var result = new java.util.LinkedHashMap<AEKey, java.math.BigInteger[]>();
        java.util.List<Map<AEKey, java.math.BigInteger>> previews = java.util.Arrays
            .asList(logic.getExactStoredPreview(), logic.getExactPendingPreview(), logic.getExactActivePreview());
        for (int kind = 0; kind < 3; kind++) {
            var preview = previews.get(kind);
            if (preview == null) continue;
            for (var entry : preview.entrySet())
                result.computeIfAbsent(entry.getKey(), ignored -> new java.math.BigInteger[3])[kind] = entry.getValue();
        }
        var all = new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter();
        logic.getAllItems(all);
        for (var entry : all) {
            AEKey key = entry.getKey();
            long[] amounts = { logic.getStored(key), logic.getPendingOutputs(key), logic.getWaitingFor(key) };
            for (int kind = 0; kind < 3; kind++) {
                if (amounts[kind] <= 0) continue;
                var preview = previews.get(kind);
                if (preview != null && preview.containsKey(key)) continue;
                var row = result.computeIfAbsent(key, ignored -> new java.math.BigInteger[3]);
                if (row[kind] == null) row[kind] = java.math.BigInteger.valueOf(amounts[kind]);
            }
        }
        return result;
    }

    private CompoundTag cpu(ECOCraftingCPU cpu) {
        CompoundTag row = new CompoundTag();
        row.putBoolean("busy", cpu.isBusy());
        row.putInt(
            "tier",
            cpu.getTier()
                .getTier());
        row.putString(
            "name",
            cpu.getName() == null ? ""
                : cpu.getName()
                    .getString());
        row.putLong("storage", cpu.getAvailableStorage());
        row.putInt("parallel", cpu.getCoProcessors());
        row.putString(
            "mode",
            cpu.getSelectionMode()
                .name());
        var status = cpu.getJobStatus();
        row.putBoolean(
            "suspended",
            cpu.getLogic()
                .isJobSuspended());
        row.putString(
            "error",
            cpu.getLogic()
                .getPermanentExecutionError() == null ? ""
                    : cpu.getLogic()
                        .getPermanentExecutionError());
        row.putLong(
            "bytes",
            cpu.getPlan() == null ? 0
                : cpu.getPlan()
                    .bytes());
        if (status != null) {
            row.putString(
                "output",
                status.crafting()
                    .what()
                    .getDisplayName());
            row.putString("requested", Long.toString(status.totalItems()));
            row.putString("completed", Long.toString(status.progress()));
            row.putString("remaining", Long.toString(Math.max(0, status.totalItems() - status.progress())));
            row.putLong("elapsed", status.elapsedTimeNanos());
            row.put(
                "outputKey",
                status.crafting()
                    .what()
                    .toTagGeneric(RegistryAccess.EMPTY));
        }
        cpu.getProgressView()
            .bigOrder()
            .ifPresent(order -> {
                row.putString(
                    "requested",
                    order.requested()
                        .toString());
                row.putString(
                    "completed",
                    order.completed()
                        .toString());
                row.putString(
                    "remaining",
                    order.remaining()
                        .toString());
                row.putString(
                    "state",
                    order.state()
                        .name());
                row.putString("waiting", order.waitingReason());
            });
        return row;
    }

    @Override
    protected void apply(int action, int argument) {
        var cluster = host.getCluster();
        switch (action) {
            case 0:
                host.toggleNetworkIgnoringPatternSubstitutions();
                break;
            case 1:
                host.toggleNetworkCyclePlanning();
                break;
            case 2:
                host.toggleNetworkFastPlanner();
                break;
            case 3:
                if (cluster != null) cluster.cycleSelectionMode();
                else {
                    var modes = cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode.values();
                    host.setCpuSelectionMode(
                        modes[(host.getCpuSelectionMode()
                            .ordinal() + 1) % modes.length]);
                }
                break;
            case 4:
                offset = Math.max(0, Math.min(65528, argument));
                break;
            case 5:
                selected = argument;
                itemOffset = 0;
                break;
            case 6:
            case 7:
                ECOCraftingCPU cpu = targets.get(argument);
                if (cpu != null && cpu.isBusy() && displayedCpus().contains(cpu)) {
                    if (action == 6) cpu.getLogic()
                        .setJobSuspended(
                            !cpu.getLogic()
                                .isJobSuspended());
                    else cpu.cancelJob();
                }
                break;
            case 8:
                itemOffset = Math.max(0, Math.min(65528, argument));
                break;
            case 9:
                host.setNetworkFrequency(
                    cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator
                        .normalize(host.getNetworkFrequency() + Integer.signum(argument)));
                break;
            case 10:
                clickDrive(argument, false);
                break;
            case 11:
                clickDrive(argument, true);
                break;
            case 12:
                driveOffset = Math.max(0, Math.min(65527, argument));
                break;
            case 13:
                visibleRows = argument == 9 ? 9 : 6;
                break;
            case 14:
                itemOffset = Math.max(0, Math.min(65520, argument));
                break;
            case 15:
                host.toggleNetworkSmartDoubling();
                break;
            case 16:
            case 17:
            case 18:
                ResourceTarget resource = resourceTargets.get(argument);
                if (resource != null && resource.cpu == targets.get(selected)
                    && displayedCpus().contains(resource.cpu)
                    && resource.job.equals(
                        resource.cpu.getLogic()
                            .getProcessingJobId())) {
                    if (action == 18) {
                        int pages = resource.cpu.getLogic()
                            .getProcessingProgress(resource.key)
                            .pages();
                        resource.detailPage = (resource.detailPage + 1) % pages;
                    } else resource.cpu.getLogic()
                        .setProcessingStepStopped(resource.key, action == 16);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown computation page action: " + action);
        }
        host.saveChanges();
    }

    public CompoundTag cpu(int index) {
        ListTag list = state().getList("cpus", 10);
        return index >= 0 && index < list.size() ? list.getCompound(index) : new CompoundTag();
    }

    private static final class ResourceTarget {

        final int token;
        final ECOCraftingCPU cpu;
        final java.util.UUID job;
        final AEKey key;
        int detailPage;

        ResourceTarget(int token, ECOCraftingCPU cpu, java.util.UUID job, AEKey key) {
            this.token = token;
            this.cpu = cpu;
            this.job = job;
            this.key = key;
        }
    }

    public CompoundTag drive(int index) {
        ListTag list = state().getList("drives", 10);
        return index >= 0 && index < list.size() ? list.getCompound(index) : new CompoundTag();
    }

    private void clickDrive(int token, boolean shift) {
        DriveTarget target = driveTargets.get(token);
        var cluster = host.getCluster();
        if (target == null || cluster == null
            || cluster.isDestroyed()
            || !cluster.getDrives()
                .contains(target.drive)
            || target.drive.isInvalid()
            || target.drive.getWorldObj() != host.getWorldObj()
            || !ItemStack.areItemStacksEqual(target.expected, target.drive.getCellStack())) return;
        var player = getSyncManager().getPlayer();
        var inventory = target.drive.getInternalInventory();
        ItemStack cursor = player.inventory.getItemStack();
        if (ItemStacks.isEmpty(target.drive.getCellStack())) {
            if (ItemStacks.isEmpty(cursor)) return;
            ItemStack simulated = inventory.insertItem(0, cursor, true);
            if (ItemStacks.count(simulated) >= ItemStacks.count(cursor)) return;
            player.inventory.setItemStack(inventory.insertItem(0, cursor, false));
        } else {
            if (!target.drive.canExtractCell() || !shift && !ItemStacks.isEmpty(cursor)) return;
            int destination = -1;
            if (shift) {
                for (int slot = 0; slot < player.inventory.mainInventory.length; slot++)
                    if (ItemStacks.isEmpty(player.inventory.getStackInSlot(slot))) {
                        destination = slot;
                        break;
                    }
                if (destination < 0) return;
            }
            ItemStack extracted = inventory.extractItem(0, 1, false);
            if (ItemStacks.isEmpty(extracted)) return;
            if (shift) player.inventory.setInventorySlotContents(destination, extracted);
            else player.inventory.setItemStack(extracted);
        }
        player.inventory.markDirty();
        cn.dancingsnow.neoecoae.platform.minecraft.NativePlayerInventory.syncMenu(player);
    }

    private static final class DriveTarget {

        private final int token;
        private final ECOComputationDriveBlockEntity drive;
        private final ItemStack expected;

        private DriveTarget(int token, ECOComputationDriveBlockEntity drive, ItemStack expected) {
            this.token = token;
            this.drive = drive;
            this.expected = expected;
        }
    }
}
