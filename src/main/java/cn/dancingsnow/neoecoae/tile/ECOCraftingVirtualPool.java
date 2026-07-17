package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlannerHook;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingOutputAllocation;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingOutputFlushContext;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingOwnershipRegistry;
import cn.dancingsnow.neoecoae.energy.ECOEnergyProfile;

/** One persistent crafting executor for the entire formed crafting host. */
final class ECOCraftingVirtualPool {

    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_TOTAL_PROGRESS = "TotalProgress";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_JOB_ID = "JobId";
    private static final String TAG_INPUTS = "Inputs";
    private static final String TAG_PENDING = "Pending";
    private static final String TAG_STATE = "State";
    private static final String TAG_OCCUPIED_SLOTS = "OccupiedSlots";
    private static final int MAX_PERSISTED_SLOTS = 65536;
    private static final int OWNERSHIP_RESTORE_GRACE_TICKS = 200;

    private final List<WorkEntry> entries = new ArrayList<WorkEntry>();
    private int ownershipGraceTicks;

    boolean accept(TileECOController controller, ICraftingPatternDetails details, InventoryCrafting table,
        int craftCount, String jobId) {
        if (controller == null || details == null
            || table == null
            || craftCount <= 0
            || craftCount > controller.getCraftingCurrentBatchSlots()) {
            return false;
        }
        ItemStack output = details.getOutput(table, controller.getWorldObj());
        if (output == null) {
            IAEItemStack[] outputs = details.getCondensedOutputs();
            if (outputs != null && outputs.length > 0 && outputs[0] != null) {
                output = outputs[0].getItemStack();
            }
        }
        if (output == null) {
            return false;
        }
        try {
            ECOFastPathPlannerHook.recordRuntimeResult(details, table, controller.getWorldObj(), output);
        } catch (RuntimeException ignored) {
            // Runtime verification only enables later batching. A verifier failure must not
            // reject the baseline craft that AE2 has already authorised and extracted.
        }
        if (!controller.consumeCraftingCoolantForWork(craftCount)) {
            return false;
        }
        List<ItemStack> inputs = multiplyStacks(snapshotInventory(table), craftCount);
        List<ItemStack> pending = new ArrayList<ItemStack>();
        pending.addAll(TileCraftingWorker.multiplyStack(output, craftCount));
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            ItemStack container = Platform.getContainerItem(table.getStackInSlot(slot));
            if (container != null && container.stackSize > 0) {
                pending.addAll(TileCraftingWorker.multiplyStack(container, craftCount));
            }
        }
        this.entries.add(
            new WorkEntry(
                0,
                ECOEnergyProfile.CRAFTING_WORK_MAX_PROGRESS,
                output,
                jobId,
                inputs,
                pending,
                WorkState.ACTIVE,
                craftCount));
        ECOCraftingOwnershipRegistry.register(jobId, controller);
        controller.onCraftingVirtualPoolStateChanged();
        return true;
    }

    void tick(TileECOController controller) {
        if (controller == null || this.entries.isEmpty()) {
            return;
        }
        long started = System.nanoTime();
        this.recoverOrphans(controller);
        int bonusValue = controller.getCraftingWorkBonusValue();
        int powerMultiplier = controller.getCraftingWorkPowerMultiplier();
        double totalPowerRequest = 0D;
        for (WorkEntry entry : this.entries) {
            if (entry.state == WorkState.ACTIVE && entry.progress < entry.totalProgress) {
                totalPowerRequest += powerRequest(entry, bonusValue, powerMultiplier);
            }
        }
        double extractedPower = totalPowerRequest <= 0D ? 0D
            : controller.extractCraftingEnergy(totalPowerRequest, false);
        double powerRatio = totalPowerRequest <= 0D ? 0D
            : Math.max(0D, Math.min(1D, extractedPower / totalPowerRequest));
        boolean changed = false;
        boolean progressDirty = false;

        for (WorkEntry entry : this.entries) {
            if (entry.state != WorkState.ACTIVE) {
                continue;
            }
            if (entry.progress < entry.totalProgress) {
                int previous = entry.progress;
                int gained = ECOEnergyProfile.craftingWorkPowerFromExtracted(
                    powerRequest(entry, bonusValue, powerMultiplier) * powerRatio,
                    entry.occupiedSlots,
                    powerMultiplier);
                entry.progress = Math.min(entry.totalProgress, entry.progress + gained);
                progressDirty |= progressBucket(previous, entry.totalProgress)
                    != progressBucket(entry.progress, entry.totalProgress);
                if (entry.progress < entry.totalProgress) {
                    continue;
                }
            }
            entry.state = WorkState.OUTPUT_READY;
            changed = true;
        }

        try (ECOCraftingOutputFlushContext.Scope ignored = ECOCraftingOutputFlushContext.enter()) {
            changed |= this.flushOutputs(controller);
        }
        if (changed) {
            controller.onCraftingVirtualPoolStateChanged();
        } else if (progressDirty) {
            controller.markDirty();
        }
        controller.recordCraftingPerformanceSample(System.nanoTime() - started);
    }

    int occupiedSlots() {
        int total = 0;
        for (WorkEntry entry : this.entries) {
            total = Integer.MAX_VALUE - total < entry.occupiedSlots ? Integer.MAX_VALUE : total + entry.occupiedSlots;
        }
        return total;
    }

    boolean isRunning() {
        return !this.entries.isEmpty();
    }

    List<TileCraftingWorker.WorkSnapshot> snapshots(int queueCapacity, int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0 || this.entries.isEmpty()) {
            return new ArrayList<TileCraftingWorker.WorkSnapshot>();
        }
        List<SnapshotGroup> groups = new ArrayList<SnapshotGroup>();
        for (WorkEntry entry : this.entries) {
            SnapshotGroup group = findSnapshotGroup(groups, entry);
            if (group == null) {
                if (groups.size() >= safeLimit) {
                    continue;
                }
                group = new SnapshotGroup(entry.output, entry.jobId, entry.state);
                groups.add(group);
            }
            group.occupiedSlots = saturatedAdd(group.occupiedSlots, entry.occupiedSlots);
            group.weightedProgress += (long) entry.progress * entry.occupiedSlots;
            group.weightedTotal += (long) Math.max(1, entry.totalProgress) * entry.occupiedSlots;
        }
        List<TileCraftingWorker.WorkSnapshot> result = new ArrayList<TileCraftingWorker.WorkSnapshot>(groups.size());
        for (SnapshotGroup group : groups) {
            int totalProgress = group.weightedTotal <= 0L ? 0 : 1000;
            int progress = totalProgress <= 0 ? 0
                : (int) Math
                    .max(0L, Math.min(totalProgress, group.weightedProgress * totalProgress / group.weightedTotal));
            result.add(
                TileCraftingWorker.WorkSnapshot.create(
                    group.output,
                    group.occupiedSlots,
                    Math.max(group.occupiedSlots, queueCapacity),
                    progress,
                    totalProgress));
        }
        return result;
    }

    void recoverJob(String jobId) {
        if (jobId == null) {
            return;
        }
        for (WorkEntry entry : this.entries) {
            if (!jobId.equals(entry.jobId) || entry.state.isRecovering()) {
                continue;
            }
            if (entry.state == WorkState.ACTIVE && entry.progress < entry.totalProgress) {
                entry.pending.clear();
                entry.pending.addAll(copyStacks(entry.inputs));
                entry.state = WorkState.RECOVERING_INPUTS;
            } else {
                entry.state = WorkState.RECOVERING_OUTPUTS;
            }
        }
    }

    void recoverUnfinishedInputs(String jobId) {
        if (jobId == null) {
            return;
        }
        for (WorkEntry entry : this.entries) {
            if (jobId.equals(entry.jobId) && entry.state == WorkState.ACTIVE && entry.progress < entry.totalProgress) {
                entry.pending.clear();
                entry.pending.addAll(copyStacks(entry.inputs));
                entry.state = WorkState.RECOVERING_INPUTS;
            }
        }
    }

    void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (WorkEntry entry : this.entries) {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger(TAG_PROGRESS, entry.progress);
            data.setInteger(TAG_TOTAL_PROGRESS, entry.totalProgress);
            data.setInteger(TAG_OCCUPIED_SLOTS, entry.occupiedSlots);
            data.setString(TAG_STATE, entry.state.name());
            if (entry.jobId != null) {
                data.setString(TAG_JOB_ID, entry.jobId);
            }
            if (entry.output != null) {
                NBTTagCompound output = new NBTTagCompound();
                entry.output.writeToNBT(output);
                data.setTag(TAG_OUTPUT, output);
            }
            data.setTag(TAG_INPUTS, TileCraftingWorker.writeStacks(entry.inputs));
            data.setTag(TAG_PENDING, TileCraftingWorker.writeStacks(entry.pending));
            list.appendTag(data);
        }
        tag.setTag(TAG_ENTRIES, list);
    }

    void readFromNBT(NBTTagCompound tag, TileECOController controller) {
        this.entries.clear();
        this.ownershipGraceTicks = OWNERSHIP_RESTORE_GRACE_TICKS;
        NBTTagList list = tag.getTagList(TAG_ENTRIES, Constants.NBT.TAG_COMPOUND);
        int occupied = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound data = list.getCompoundTagAt(i);
            int slots = Math.max(1, data.getInteger(TAG_OCCUPIED_SLOTS));
            if (slots > MAX_PERSISTED_SLOTS - occupied) {
                break;
            }
            ItemStack output = data.hasKey(TAG_OUTPUT) ? ItemStack.loadItemStackFromNBT(data.getCompoundTag(TAG_OUTPUT))
                : null;
            if (output == null) {
                continue;
            }
            String jobId = data.hasKey(TAG_JOB_ID) ? data.getString(TAG_JOB_ID) : null;
            WorkEntry entry = new WorkEntry(
                Math.max(0, data.getInteger(TAG_PROGRESS)),
                Math.max(0, data.getInteger(TAG_TOTAL_PROGRESS)),
                output,
                jobId,
                TileCraftingWorker.readStacks(data.getTagList(TAG_INPUTS, Constants.NBT.TAG_COMPOUND)),
                TileCraftingWorker.readStacks(data.getTagList(TAG_PENDING, Constants.NBT.TAG_COMPOUND)),
                WorkState.byName(data.getString(TAG_STATE)),
                slots);
            this.entries.add(entry);
            occupied += slots;
        }
    }

    private boolean flushOutputs(TileECOController controller) {
        List<OutputGroup> groups = new ArrayList<OutputGroup>();
        for (WorkEntry entry : this.entries) {
            if (!entry.state.hasPendingOutput()) {
                continue;
            }
            boolean recovery = entry.state.isRecovering();
            String jobId = recovery ? null : entry.jobId;
            for (ItemStack stack : entry.pending) {
                if (stack == null || stack.stackSize <= 0) {
                    continue;
                }
                OutputGroup group = findGroup(groups, stack, recovery, jobId);
                if (group == null) {
                    group = new OutputGroup(stack, recovery, jobId);
                    groups.add(group);
                }
                group.requested += stack.stackSize;
                if (!group.entries.contains(entry)) {
                    group.entries.add(entry);
                }
            }
        }
        boolean changed = false;
        for (OutputGroup group : groups) {
            long accepted = insert(controller, group);
            if (accepted <= 0L) {
                continue;
            }
            long[] demands = new long[group.entries.size()];
            for (int i = 0; i < demands.length; i++) {
                demands[i] = TileCraftingWorker.countMatching(group.entries.get(i).pending, group.prototype);
            }
            long[] shares = ECOCraftingOutputAllocation.proportional(demands, accepted);
            for (int i = 0; i < shares.length; i++) {
                if (shares[i] > 0L) {
                    TileCraftingWorker.removeMatching(group.entries.get(i).pending, group.prototype, shares[i]);
                    changed = true;
                }
            }
        }
        for (Iterator<WorkEntry> it = this.entries.iterator(); it.hasNext();) {
            WorkEntry entry = it.next();
            if (entry.state.hasPendingOutput() && entry.pending.isEmpty()) {
                it.remove();
                ECOCraftingOwnershipRegistry.unregister(entry.jobId, controller);
                changed = true;
            }
        }
        return changed;
    }

    private void recoverOrphans(TileECOController controller) {
        if (this.ownershipGraceTicks > 0) {
            this.ownershipGraceTicks--;
        }
        if (controller.getWorldObj() == null || controller.getWorldObj()
            .getTotalWorldTime() % 20L != 0L) {
            return;
        }
        List<String> orphaned = new ArrayList<String>();
        for (WorkEntry entry : this.entries) {
            if (entry.jobId != null) {
                ECOCraftingOwnershipRegistry.register(entry.jobId, controller);
                if (this.ownershipGraceTicks <= 0 && !ECOCraftingOwnershipRegistry.isActive(entry.jobId)
                    && !orphaned.contains(entry.jobId)) {
                    orphaned.add(entry.jobId);
                }
            }
        }
        for (String jobId : orphaned) {
            this.recoverJob(jobId);
        }
    }

    private static double powerRequest(WorkEntry entry, int bonusValue, int powerMultiplier) {
        return ECOEnergyProfile.craftingBatchWorkPowerRequest(1, bonusValue, entry.occupiedSlots, powerMultiplier);
    }

    private static int progressBucket(int progress, int totalProgress) {
        if (progress <= 0 || totalProgress <= 0) {
            return 0;
        }
        if (progress >= totalProgress) {
            return 10;
        }
        return (int) Math.min(9L, (long) progress * 10L / totalProgress);
    }

    private static long insert(TileECOController controller, OutputGroup group) {
        if (group.recovery) {
            return controller.acceptCraftingRecoveryAmount(group.prototype, group.requested);
        }
        if (group.jobId == null) {
            return controller.acceptCraftingOutputAmount(group.prototype, group.requested);
        }
        long accepted = ECOCraftingOwnershipRegistry.injectOwnedOutput(group.jobId, group.prototype, group.requested);
        long remaining = group.requested - accepted;
        // AE2 standalone jobs consume the CPU's waitingFor amount, but CraftingLink returns
        // the final output unchanged because there is no ICraftingRequester. That return value
        // means "store this in the network", not "the CPU rejected the craft". Always route any
        // returned remainder to normal network storage; otherwise the first full batch occupies
        // the virtual pool forever.
        if (remaining > 0L) {
            accepted += controller.acceptCraftingRecoveryAmount(group.prototype, remaining);
        }
        return accepted;
    }

    private static OutputGroup findGroup(List<OutputGroup> groups, ItemStack stack, boolean recovery, String jobId) {
        for (OutputGroup group : groups) {
            if (group.recovery == recovery && sameJob(group.jobId, jobId)
                && TileCraftingWorker.sameStackType(group.prototype, stack)) {
                return group;
            }
        }
        return null;
    }

    private static SnapshotGroup findSnapshotGroup(List<SnapshotGroup> groups, WorkEntry entry) {
        for (SnapshotGroup group : groups) {
            if (group.state == entry.state && sameJob(group.jobId, entry.jobId)
                && TileCraftingWorker.sameStackType(group.output, entry.output)) {
                return group;
            }
        }
        return null;
    }

    private static int saturatedAdd(int left, int right) {
        if (right <= 0) {
            return left;
        }
        return Integer.MAX_VALUE - left < right ? Integer.MAX_VALUE : left + right;
    }

    private static boolean sameJob(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static List<ItemStack> snapshotInventory(InventoryCrafting table) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            ItemStack stack = table.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private static List<ItemStack> multiplyStacks(List<ItemStack> stacks, int multiplier) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (ItemStack stack : stacks) {
            result.addAll(TileCraftingWorker.multiplyStack(stack, multiplier));
        }
        return result;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (ItemStack stack : stacks) {
            if (stack != null && stack.stackSize > 0) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private enum WorkState {

        ACTIVE,
        OUTPUT_READY,
        RECOVERING_INPUTS,
        RECOVERING_OUTPUTS;

        private boolean hasPendingOutput() {
            return this != ACTIVE;
        }

        private boolean isRecovering() {
            return this == RECOVERING_INPUTS || this == RECOVERING_OUTPUTS;
        }

        private static WorkState byName(String name) {
            try {
                return name == null || name.length() == 0 ? ACTIVE : valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return ACTIVE;
            }
        }
    }

    private static final class WorkEntry {

        private int progress;
        private final int totalProgress;
        private final ItemStack output;
        private final String jobId;
        private final List<ItemStack> inputs;
        private final List<ItemStack> pending;
        private WorkState state;
        private final int occupiedSlots;

        private WorkEntry(int progress, int totalProgress, ItemStack output, String jobId, List<ItemStack> inputs,
            List<ItemStack> pending, WorkState state, int occupiedSlots) {
            this.totalProgress = Math.max(0, totalProgress);
            this.progress = Math.max(0, Math.min(progress, this.totalProgress));
            this.output = output.copy();
            this.jobId = jobId == null || jobId.length() == 0 ? null : jobId;
            this.inputs = copyStacks(inputs);
            this.pending = copyStacks(pending);
            this.state = state == null ? WorkState.ACTIVE : state;
            this.occupiedSlots = Math.max(1, occupiedSlots);
        }
    }

    private static final class OutputGroup {

        private final ItemStack prototype;
        private final boolean recovery;
        private final String jobId;
        private final List<WorkEntry> entries = new ArrayList<WorkEntry>();
        private long requested;

        private OutputGroup(ItemStack prototype, boolean recovery, String jobId) {
            this.prototype = prototype.copy();
            this.prototype.stackSize = 1;
            this.recovery = recovery;
            this.jobId = jobId;
        }
    }

    private static final class SnapshotGroup {

        private final ItemStack output;
        private final String jobId;
        private final WorkState state;
        private int occupiedSlots;
        private long weightedProgress;
        private long weightedTotal;

        private SnapshotGroup(ItemStack output, String jobId, WorkState state) {
            this.output = output.copy();
            this.jobId = jobId;
            this.state = state;
        }
    }
}
