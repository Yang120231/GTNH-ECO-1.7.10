package cn.dancingsnow.neoecoae.platform.ui.computation;

import java.util.ArrayList;
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
import cn.dancingsnow.neoecoae.platform.ui.NativeSnapshotSession;

/** Bounded task pages and exact order quantities, with stable session identities for destructive actions. */
public final class NativeComputationSession extends NativeSnapshotSession {

    public static final int PAGE_SIZE = 8;
    private final ECOComputationSystemBlockEntity host;
    private final IdentityHashMap<ECOCraftingCPU, Integer> identities = new IdentityHashMap<>();
    private final Map<Integer, ECOCraftingCPU> targets = new HashMap<>();
    private final IdentityHashMap<ECOComputationDriveBlockEntity, DriveTarget> driveIdentities = new IdentityHashMap<>();
    private final Map<Integer, DriveTarget> driveTargets = new HashMap<>();
    private int serial, offset, selected, itemOffset, driveOffset;

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
        tag.putBoolean("substitutions", !host.isIgnoringPatternSubstitutions());
        tag.putBoolean("cycles", host.isCyclePlanningEnabled());
        tag.putBoolean("fast", host.isFastPlannerEnabled());
        tag.putInt("frequency", host.getNetworkFrequency());
        var cluster = host.getCluster();
        ListTag cpus = new ListTag(), drives = new ListTag();
        if (cluster != null && !cluster.isDestroyed()) {
            tag.putLong("bytes", cluster.getPooledTotalStorage());
            tag.putLong("freeBytes", cluster.getAvailableStorage());
            tag.putInt("threads", cluster.getMaxThreads());
            tag.putInt("parallel", cluster.getPooledParallelism());
            tag.putString(
                "selectionMode",
                cluster.getSelectionMode()
                    .name());
            var active = new ArrayList<>(cluster.getActiveCPUs());
            identities.keySet()
                .removeIf(cpu -> !active.contains(cpu));
            targets.entrySet()
                .removeIf(entry -> !active.contains(entry.getValue()));
            offset = Math.min(Math.max(0, active.size() - 1) / PAGE_SIZE * PAGE_SIZE, offset);
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
                detail.put("stored", resources(chosen, 0));
                detail.put("pending", resources(chosen, 1));
                detail.put("active", resources(chosen, 2));
                detail.putInt("itemOffset", itemOffset);
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
                var rowDrives = rowIndex == 0 ? cluster.getUpperDrives() : cluster.getLowerDrives();
                CompoundTag row = new CompoundTag();
                int index = driveOffset + column;
                if (index >= rowDrives.size()) {
                    drives.add(row);
                    continue;
                }
                var drive = rowDrives.get(index);
                DriveTarget target = driveIdentities.get(drive);
                if (target == null || !ItemStack.areItemStacksEqual(target.expected, drive.getCellStack())) {
                    if (target != null) driveTargets.remove(target.token);
                    target = new DriveTarget(++serial, drive, ItemStack.copyItemStack(drive.getCellStack()));
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
        return tag;
    }

    private CompoundTag cpu(ECOCraftingCPU cpu) {
        CompoundTag row = new CompoundTag();
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

    private ListTag resources(ECOCraftingCPU cpu, int kind) {
        Map<AEKey, java.math.BigInteger> resources = new HashMap<>(
            kind == 0 ? cpu.getLogic()
                .getExactStoredPreview()
                : kind == 1 ? cpu.getLogic()
                    .getExactPendingPreview()
                    : cpu.getLogic()
                        .getExactActivePreview());
        var all = new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter();
        cpu.getLogic()
            .getAllItems(all);
        for (var entry : all) {
            AEKey key = entry.getKey();
            long amount = kind == 0 ? cpu.getLogic()
                .getStored(key)
                : kind == 1 ? cpu.getLogic()
                    .getPendingOutputs(key)
                    : cpu.getLogic()
                        .getWaitingFor(key);
            if (amount > 0) resources.putIfAbsent(key, java.math.BigInteger.valueOf(amount));
        }
        ListTag list = new ListTag();
        var sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(
            java.util.Comparator.comparing(
                entry -> entry.getKey()
                    .getDisplayName()));
        for (int index = itemOffset; index < Math.min(sorted.size(), itemOffset + 8); index++) {
            var entry = sorted.get(index);
            CompoundTag row = new CompoundTag();
            row.putString(
                "name",
                entry.getKey()
                    .getDisplayName());
            row.putString(
                "amount",
                entry.getValue()
                    .toString());
            list.add(row);
        }
        return list;
    }

    @Override
    protected void apply(int action, int argument) {
        var cluster = host.getCluster();
        switch (action) {
            case 0:
                host.setIgnoringPatternSubstitutions(!host.isIgnoringPatternSubstitutions());
                break;
            case 1:
                host.setCyclePlanningEnabled(!host.isCyclePlanningEnabled());
                break;
            case 2:
                host.setFastPlannerEnabled(!host.isFastPlannerEnabled());
                break;
            case 3:
                if (cluster != null) cluster.cycleSelectionMode();
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
                if (cpu != null && cluster != null
                    && cluster.getActiveCPUs()
                        .contains(cpu)) {
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
                host.setNetworkFrequency(host.getNetworkFrequency() + Integer.signum(argument));
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
            default:
                throw new IllegalArgumentException("Unknown computation page action: " + action);
        }
        host.saveChanges();
    }

    public CompoundTag cpu(int index) {
        ListTag list = state().getList("cpus", 10);
        return index >= 0 && index < list.size() ? list.getCompound(index) : new CompoundTag();
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
