package cn.dancingsnow.neoecoae.computation.ae2;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.DoubleUnaryOperator;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.config.CraftingAllow;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.WorldCoord;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.computation.ComputationTaskInfo;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathBatchPolicy;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathConfig;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlan;
import cn.dancingsnow.neoecoae.crafting.fastpath.ECOFastPathPlannerHook;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingBatchCoordinator;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingBatchTransaction;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingExecutionContext;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingOutputFlushContext;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingOwnershipRegistry;
import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

public class ECOComputationVirtualCpu extends CraftingCPUCluster implements ECOCraftingBatchCoordinator {

    private static final int RESTORE_LINK_GRACE_TICKS = 200;
    private static final String TAG_COMPLETION_DEFERRED = "EcoCompletionDeferred";
    private static final Field TASK_PROGRESS_VALUE = taskProgressValueField();

    private final ECOComputationCpuPool pool;
    private final TileECOInterface host;
    private final int serial;

    private long reservedStorage;
    private int coProcessors;
    private boolean active;
    private IGrid grid;
    private boolean released;
    private boolean reservedForJob;
    private CraftingAllow craftingAllowMode = CraftingAllow.ALLOW_ALL;
    private int restoreLinkGraceTicks;
    private IEnergyGrid batchEnergyGrid;
    private int craftsThisTick;
    private boolean cleanupPending;
    private boolean completionDeferred;

    ECOComputationVirtualCpu(ECOComputationCpuPool pool, TileECOInterface host, int serial) {
        super(
            new WorldCoord(host.xCoord, host.yCoord, host.zCoord),
            new WorldCoord(host.xCoord, host.yCoord, host.zCoord));
        this.pool = pool;
        this.host = host;
        this.serial = serial;
        this.machineSrc = new MachineSource(host);
        this.isComplete = true;
    }

    void configureIdle(long storage, int coProcessors, IGrid grid, boolean active,
        ComputationCpuSelectionMode cpuSelectionMode) {
        this.reservedStorage = Math.max(0L, storage);
        this.availableStorage = this.reservedStorage;
        this.reservedForJob = false;
        this.completionDeferred = false;
        this.updateResources(coProcessors, grid, active, cpuSelectionMode);
        this.myName = this.pool.nameFor(this.serial, false);
    }

    boolean belongsToPool(ECOComputationCpuPool owner) {
        return this.pool == owner;
    }

    long reservedStorage() {
        return this.reservedStorage;
    }

    int serial() {
        return this.serial;
    }

    long usedStorageForDisplay() {
        return this.isBusy() ? this.getUsedStorage() : 0L;
    }

    boolean shouldPersist() {
        return !this.released && (this.hasAllocatedJobState() || super.isBusy()
            || this.getLastCraftingLink() != null
            || this.getUsedStorage() > 0L
            || !this.inventory.isEmpty());
    }

    ComputationTaskInfo taskEntry() {
        if (!this.isBusy()) {
            return null;
        }
        IAEItemStack output = Platform.stackConvertPacket(this.getFinalMultiOutput());
        ItemStack outputStack = output == null ? null : output.getItemStack();
        return new ComputationTaskInfo(
            outputStack,
            output == null ? 0L : output.getStackSize(),
            this.getElapsedTime(),
            this.getName(),
            this.serial,
            this.reservedStorage,
            this.coProcessors,
            cpuSelectionMode(this.craftingAllowMode),
            this.waiting ? ComputationTaskInfo.Status.WAITING : ComputationTaskInfo.Status.RUNNING);
    }

    void updateResources(int coProcessors, IGrid grid, boolean active, ComputationCpuSelectionMode cpuSelectionMode) {
        this.coProcessors = Math.max(0, coProcessors);
        this.accelerator = this.coProcessors;
        this.grid = grid;
        this.active = active;
        this.craftingAllowMode = allowMode(cpuSelectionMode);
    }

    boolean restoreFromNBT(NBTTagCompound data, long reservedStorage, int coProcessors, IGrid grid, boolean active,
        ComputationCpuSelectionMode cpuSelectionMode) {
        this.reservedStorage = Math.max(0L, reservedStorage);
        this.availableStorage = this.reservedStorage;
        this.updateResources(coProcessors, grid, active, cpuSelectionMode);
        this.released = false;
        this.machineSrc = new MachineSource(this.host);
        try {
            super.readFromNBT(data);
        } catch (RuntimeException e) {
            return false;
        }
        this.completionDeferred = data.getBoolean(TAG_COMPLETION_DEFERRED);
        this.reservedForJob = this.hasRestoredJobState();
        this.cleanupPending = this.isComplete && this.reservedForJob;
        this.restoreLinkGraceTicks = this.reservedForJob && !this.isComplete ? RESTORE_LINK_GRACE_TICKS : 0;
        this.myName = this.pool.nameFor(this.serial, true);
        ECOCraftingOwnershipRegistry.heartbeat(this.currentCraftingJobId(), this);
        return this.shouldPersist();
    }

    NBTTagCompound writeRuntimeNBT() {
        NBTTagCompound data = new NBTTagCompound();
        super.writeToNBT(data);
        data.setBoolean(TAG_COMPLETION_DEFERRED, this.completionDeferred);
        return data;
    }

    @Override
    public ICraftingLink submitJob(IGrid grid, ICraftingJob job, BaseActionSource src,
        ICraftingRequester requestingMachine) {
        if (this.released) {
            return null;
        }
        if (!this.pool.tryReserve(this, job)) {
            return null;
        }
        ICraftingLink link = super.submitJob(grid, job, src, requestingMachine);
        if (link == null) {
            this.releaseFromPool();
        } else {
            this.reservedForJob = true;
            this.myName = this.pool.nameFor(this.serial, true);
            ECOCraftingOwnershipRegistry.heartbeat(this.currentCraftingJobId(), this);
            this.pool.onCpuJobAccepted(this);
        }
        return link;
    }

    @Override
    protected void completeJob() {
        if (ECOCraftingOutputFlushContext.isActive()) {
            this.completionDeferred = true;
            this.markDirty();
            return;
        }
        this.completionDeferred = false;
        String jobId = this.currentCraftingJobId();
        boolean recoveredUnfinished = false;
        if (this.finalOutput.isEmpty() && this.hasPositiveTaskProgress()) {
            ECOCraftingOwnershipRegistry.completeAndRecoverUnfinished(jobId);
            recoveredUnfinished = true;
            this.clearTaskProgress();
        }
        super.completeJob();
        if (!this.isComplete) {
            return;
        }
        if (!recoveredUnfinished) {
            ECOCraftingOwnershipRegistry.completeAndRecoverUnfinished(jobId);
        }
        this.beginCleanup();
    }

    @Override
    public IAEStack<?> injectItems(IAEStack<?> input, Actionable type, BaseActionSource src) {
        return this.isComplete ? input : super.injectItems(input, type, src);
    }

    @Override
    public void cancel() {
        String jobId = this.currentCraftingJobId();
        this.completionDeferred = false;
        super.cancel();
        ECOCraftingOwnershipRegistry.cancelAndRecover(jobId);
        this.beginCleanup();
    }

    @Override
    public void destroy() {
        String jobId = this.currentCraftingJobId();
        this.active = false;
        this.completionDeferred = false;
        if (!this.isComplete) {
            super.cancel();
        }
        ECOCraftingOwnershipRegistry.cancelAndRecover(jobId);
        this.beginCleanup();
        super.destroy();
    }

    void shutdown() {
        this.active = false;
        if (this.isBusy()) {
            this.cancel();
            return;
        }
        this.releaseFromPool();
    }

    @Override
    public void updateCraftingLogic(IGrid grid, IEnergyGrid energyGrid, CraftingGridCache craftingGrid) {
        if (!this.isActive()) {
            return;
        }
        this.craftsThisTick = 0;
        String jobId = this.currentCraftingJobId();
        ECOCraftingOwnershipRegistry.heartbeat(jobId, this);

        if (this.completionDeferred) {
            this.completeJob();
        }
        if (this.isComplete) {
            this.flushCleanup();
            return;
        }

        if (this.myLastLink != null && this.myLastLink.isCanceled()) {
            if (this.restoreLinkGraceTicks > 0) {
                this.restoreLinkGraceTicks--;
                this.pool.requestLinkRebind();
                return;
            }
            this.cancel();
            return;
        }
        this.restoreLinkGraceTicks = 0;

        this.waiting = false;
        if (this.tasks.isEmpty()) {
            return;
        }

        this.remainingOperations = this.accelerator + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        int started = this.remainingOperations;
        this.workableTasks.clear();
        this.workableTasks.putAll(this.tasks);
        this.knownBusyMediums.clear();
        if (this.remainingOperations > 0) {
            do {
                this.somethingChanged = false;
                this.executeCrafting(energyGrid, craftingGrid);
            } while (this.somethingChanged && this.remainingOperations > 0);
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - this.remainingOperations;
        this.knownBusyMediums.clear();

        if (this.remainingOperations > 0 && !this.somethingChanged) {
            this.waiting = true;
        }
    }

    @Override
    protected void executeCrafting(IEnergyGrid energyGrid, CraftingGridCache craftingGrid) {
        String jobId = this.currentCraftingJobId();
        this.batchEnergyGrid = energyGrid;
        try (ECOCraftingExecutionContext.Scope ignored = ECOCraftingExecutionContext.enter(jobId, this)) {
            this.filterDispatchableTasks(true);
            super.executeCrafting(energyGrid, craftingGrid);

            // After upstream work had the first chance, allow already-stocked intermediates to proceed.
            if (this.remainingOperations > 0) {
                this.workableTasks.clear();
                this.workableTasks.putAll(this.tasks);
                this.filterDispatchableTasks(false);
                if (!this.workableTasks.isEmpty()) {
                    super.executeCrafting(energyGrid, craftingGrid);
                }
            }
        } finally {
            this.batchEnergyGrid = null;
        }
    }

    @Override
    public ECOCraftingBatchTransaction prepareBatch(appeng.api.networking.crafting.ICraftingPatternDetails details,
        InventoryCrafting table, TileECOController controller) {
        try {
            return this.doPrepareBatch(details, table, controller);
        } catch (RuntimeException e) {
            // Fast-path preparation is optional. It must never prevent AE2 from dispatching
            // the already-authorised single craft after all speculative state was rolled back.
            NeoECOAE.LOG.warn("ECO batch preparation failed; falling back to one normal craft", e);
            return null;
        }
    }

    private ECOCraftingBatchTransaction doPrepareBatch(appeng.api.networking.crafting.ICraftingPatternDetails details,
        InventoryCrafting table, TileECOController controller) {
        ECOFastPathPlan fastPathPlan = ECOFastPathPlannerHook.tryVerifiedPlan(controller, details, table, this.grid);
        if (TASK_PROGRESS_VALUE == null || this.batchEnergyGrid == null
            || details == null
            || table == null
            || controller == null
            || !fastPathPlan.accepted()) {
            return null;
        }
        boolean processingMatrix = fastPathPlan.getPatternProfile() != null && !fastPathPlan.getPatternProfile()
            .isCraftable();
        TaskProgress progress = this.tasks.get(details);
        long taskRemaining = taskProgressValue(progress);
        int batchTickLimit = ECOFastPathConfig.batchTickLimit();
        int requested = (int) Math.min(
            Math.min(Math.max(0L, taskRemaining), remainingTickBudget(batchTickLimit, this.craftsThisTick)),
            controller.getCraftingCurrentBatchSlots());
        requested = Math.min(requested, this.maxCraftsNeededForFinalOutput(details));
        double powerPerCraft = patternPower(table);
        requested = maxAffordableCrafts(
            powerPerCraft,
            requested,
            power -> this.batchEnergyGrid.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        requested = controller.getCraftingCoolantCraftLimit(requested);
        requested = Math.min(requested, this.maxCraftsFromInventory(table, requested - 1) + 1);
        requested = ECOFastPathBatchPolicy.normalizeRequested(requested, processingMatrix);
        if (requested <= 1) {
            return null;
        }

        int extraCrafts = requested - 1;
        List<IAEItemStack> additionalInputs = additionalInputs(table, extraCrafts);
        if (additionalInputs.isEmpty() || !canExtractAll(additionalInputs)) {
            return null;
        }
        double extraPower = powerPerCraft * extraCrafts;

        List<IAEItemStack> extracted = new ArrayList<IAEItemStack>();
        try {
            for (IAEItemStack request : additionalInputs) {
                IAEItemStack result = this.inventory.extractItems(request, Actionable.MODULATE);
                if (result != null && result.getStackSize() > 0L) {
                    extracted.add(result.copy());
                    this.postChange(result, this.machineSrc);
                }
                if (result == null || result.getStackSize() != request.getStackSize()) {
                    rollbackInputs(extracted);
                    return null;
                }
            }
        } catch (RuntimeException e) {
            try {
                rollbackInputs(extracted);
            } catch (RuntimeException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        }
        if (!setTaskProgressValue(progress, taskRemaining - extraCrafts)) {
            RuntimeException failure = new IllegalStateException("ECO CPU could not reserve batch task progress");
            try {
                rollbackInputs(extracted);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        return new BatchTransaction(details, table, progress, taskRemaining, requested, extraPower, extracted);
    }

    @Override
    public void recordSlowCraftAccepted() {
        this.craftsThisTick = saturatingIntAdd(this.craftsThisTick, 1);
    }

    @Override
    public void handleBatchFailure(RuntimeException failure) {
        NeoECOAE.LOG.error("ECO batch dispatch failed and was rolled back; AE2 may retry it", failure);
        this.markDirty();
    }

    @Override
    public boolean isBatchDispatchSuspended() {
        return this.suspended;
    }

    @Override
    public boolean isSuspended() {
        return this.suspended;
    }

    @Override
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
        this.markDirty();
    }

    private void filterDispatchableTasks(boolean blockUnfinishedDependencies) {
        for (Iterator<Entry<appeng.api.networking.crafting.ICraftingPatternDetails, TaskProgress>> it = this.workableTasks
            .entrySet()
            .iterator(); it.hasNext();) {
            Entry<appeng.api.networking.crafting.ICraftingPatternDetails, TaskProgress> entry = it.next();
            if (blockUnfinishedDependencies
                && (this.hasInFlightInput(entry.getKey()) || this.hasUnfinishedDependency(entry.getKey()))) {
                it.remove();
            }
        }
    }

    private boolean hasInFlightInput(appeng.api.networking.crafting.ICraftingPatternDetails details) {
        for (IAEStack<?> input : safeInputs(details)) {
            IAEStack<?> waitingStack = this.waitingFor.findPrecise(input);
            if (waitingStack != null && waitingStack.getStackSize() > 0L) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnfinishedDependency(appeng.api.networking.crafting.ICraftingPatternDetails consumer) {
        List<IAEStack<?>> inputs = safeInputs(consumer);
        if (inputs.isEmpty()) {
            return false;
        }
        for (Entry<appeng.api.networking.crafting.ICraftingPatternDetails, TaskProgress> candidate : this.tasks
            .entrySet()) {
            if (candidate.getKey() == consumer) {
                continue;
            }
            IAEStack<?>[] outputs = candidate.getKey()
                .getCondensedAEOutputs();
            if (outputs == null) {
                continue;
            }
            for (IAEStack<?> output : outputs) {
                for (IAEStack<?> input : inputs) {
                    if (output != null && input != null && output.isSameType(input)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasPositiveTaskProgress() {
        for (TaskProgress progress : this.tasks.values()) {
            if (taskProgressValue(progress) > 0L) {
                return true;
            }
        }
        return false;
    }

    private void clearTaskProgress() {
        for (TaskProgress progress : this.tasks.values()) {
            setTaskProgressValue(progress, 0L);
        }
        this.workableTasks.clear();
    }

    private int maxCraftsNeededForFinalOutput(appeng.api.networking.crafting.ICraftingPatternDetails details) {
        if (details == null || !this.finalOutput.isFinalPattern(details)) {
            return Integer.MAX_VALUE;
        }
        IAEStack<?> finalType = this.finalOutput.getOriginalOutput();
        if (finalType == null) {
            return Integer.MAX_VALUE;
        }
        long outputPerCraft = 0L;
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (outputs != null) {
            for (IAEStack<?> output : outputs) {
                if (output != null && output.isSameType(finalType)) {
                    outputPerCraft = saturatingAdd(outputPerCraft, output.getStackSize());
                }
            }
        }
        IAEStack<?> remaining = this.finalOutput.get();
        IAEStack<?> inFlight = this.waitingFor.findPrecise(finalType);
        return maxCraftsForFinalOutputDemand(
            remaining == null ? 0L : remaining.getStackSize(),
            inFlight == null ? 0L : inFlight.getStackSize(),
            outputPerCraft);
    }

    static int maxCraftsForFinalOutputDemand(long remainingAmount, long inFlightAmount, long outputAmountPerCraft) {
        if (outputAmountPerCraft <= 0L) {
            return Integer.MAX_VALUE;
        }
        long outstanding = remainingAmount - Math.max(0L, inFlightAmount);
        if (outstanding <= 0L) {
            return 0;
        }
        long crafts = 1L + (outstanding - 1L) / outputAmountPerCraft;
        return crafts >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) crafts;
    }

    static int remainingTickBudget(int tickLimit, int acceptedCrafts) {
        return Math.max(0, Math.max(0, tickLimit) - Math.max(0, acceptedCrafts));
    }

    private static List<IAEStack<?>> safeInputs(appeng.api.networking.crafting.ICraftingPatternDetails details) {
        List<IAEStack<?>> result = new ArrayList<IAEStack<?>>();
        IAEStack<?>[] inputs = details.getAEInputs();
        if (inputs != null) {
            for (IAEStack<?> input : inputs) {
                if (input != null && input.getStackSize() > 0L) {
                    result.add(input);
                }
            }
        }
        return result;
    }

    private String currentCraftingJobId() {
        return this.myLastLink == null ? null : this.myLastLink.getCraftingID();
    }

    public boolean ownsCraftingJob(String jobId) {
        String currentJobId = this.currentCraftingJobId();
        return !this.released && jobId != null && jobId.equals(currentJobId);
    }

    private boolean canExtractAll(List<IAEItemStack> requests) {
        for (IAEItemStack request : requests) {
            IAEItemStack available = this.inventory.extractItems(request, Actionable.SIMULATE);
            if (available == null || available.getStackSize() != request.getStackSize()) {
                return false;
            }
        }
        return true;
    }

    private int maxCraftsFromInventory(InventoryCrafting table, int requestedExtraCrafts) {
        int availableCrafts = Math.max(0, requestedExtraCrafts);
        for (IAEItemStack perCraft : additionalInputs(table, 1)) {
            long desired = saturatingMultiply(perCraft.getStackSize(), availableCrafts);
            IAEItemStack request = perCraft.copy()
                .setStackSize(desired);
            IAEItemStack available = this.inventory.extractItems(request, Actionable.SIMULATE);
            long availableAmount = available == null ? 0L : available.getStackSize();
            availableCrafts = Math
                .min(availableCrafts, (int) Math.min(Integer.MAX_VALUE, availableAmount / perCraft.getStackSize()));
            if (availableCrafts <= 0) {
                return 0;
            }
        }
        return availableCrafts;
    }

    static int maxAffordableCrafts(double powerPerCraft, int requested, DoubleUnaryOperator simulatedExtraction) {
        int boundedRequested = Math.min(ECOFastPathConfig.MAX_BATCH_SIZE, Math.max(0, requested));
        if (boundedRequested <= 0 || !Double.isFinite(powerPerCraft)
            || powerPerCraft < 0D
            || simulatedExtraction == null) {
            return 0;
        }
        if (powerPerCraft == 0D) {
            return boundedRequested;
        }
        if (hasEnoughEnergy(powerPerCraft, boundedRequested, simulatedExtraction)) {
            return boundedRequested;
        }
        int low = 0;
        int high = boundedRequested - 1;
        while (low < high) {
            int candidate = low + (high - low + 1) / 2;
            if (hasEnoughEnergy(powerPerCraft, candidate, simulatedExtraction)) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    private static boolean hasEnoughEnergy(double powerPerCraft, int craftCount,
        DoubleUnaryOperator simulatedExtraction) {
        double requestedPower = powerPerCraft * craftCount;
        if (!Double.isFinite(requestedPower)) {
            return false;
        }
        double extractedPower = simulatedExtraction.applyAsDouble(requestedPower);
        return !Double.isNaN(extractedPower) && extractedPower >= requestedPower - 0.01D;
    }

    private void rollbackInputs(List<IAEItemStack> inputs) {
        RuntimeException failure = null;
        for (int i = inputs.size() - 1; i >= 0; i--) {
            IAEItemStack input = inputs.get(i);
            try {
                this.inventory.injectItems(input, Actionable.MODULATE);
                inputs.remove(i);
                this.postChange(input, this.machineSrc);
            } catch (RuntimeException e) {
                failure = appendFailure(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException appendFailure(RuntimeException failure, RuntimeException next) {
        if (failure == null) {
            return next;
        }
        failure.addSuppressed(next);
        return failure;
    }

    private static List<IAEItemStack> additionalInputs(InventoryCrafting table, int multiplier) {
        List<IAEItemStack> inputs = new ArrayList<IAEItemStack>();
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            ItemStack stack = table.getStackInSlot(slot);
            IAEItemStack input = AEItemStack.create(stack);
            if (input == null || input.getStackSize() <= 0L) {
                continue;
            }
            long amount = saturatingMultiply(input.getStackSize(), multiplier);
            IAEItemStack existing = findSameType(inputs, input);
            if (existing == null) {
                inputs.add(
                    input.copy()
                        .setStackSize(amount));
            } else {
                existing.setStackSize(saturatingAdd(existing.getStackSize(), amount));
            }
        }
        return inputs;
    }

    private static IAEItemStack findSameType(List<IAEItemStack> stacks, IAEItemStack target) {
        for (IAEItemStack stack : stacks) {
            if (stack.isSameType(target)) {
                return stack;
            }
        }
        return null;
    }

    private static double patternPower(InventoryCrafting table) {
        double power = 0D;
        for (int slot = 0; slot < table.getSizeInventory(); slot++) {
            IAEItemStack input = AEItemStack.create(table.getStackInSlot(slot));
            if (input != null && input.getAmountPerUnit() > 0L) {
                power += (double) input.getStackSize() / input.getAmountPerUnit();
            }
        }
        return power;
    }

    private void accountAdditionalOutputs(appeng.api.networking.crafting.ICraftingPatternDetails details,
        InventoryCrafting table, int extraCrafts) {
        IAEStack<?>[] outputs = details.getCondensedAEOutputs();
        if (outputs != null) {
            for (IAEStack<?> output : outputs) {
                this.addWaitingOutput(output, extraCrafts);
            }
        }
        if (details.isCraftable()) {
            for (int slot = 0; slot < table.getSizeInventory(); slot++) {
                this.addWaitingOutput(
                    AEItemStack.create(Platform.getContainerItem(table.getStackInSlot(slot))),
                    extraCrafts);
            }
        }
    }

    private void addWaitingOutput(IAEStack<?> output, int multiplier) {
        if (output == null || output.getStackSize() <= 0L || multiplier <= 0) {
            return;
        }
        IAEStack<?> total = output.copy()
            .setStackSize(saturatingMultiply(output.getStackSize(), multiplier));
        this.postChange(total, this.machineSrc);
        this.waitingFor.add(total);
        this.postCraftingStatusChange(total);
    }

    static long taskProgressValue(TaskProgress progress) {
        if (progress == null || TASK_PROGRESS_VALUE == null) {
            return 0L;
        }
        try {
            return TASK_PROGRESS_VALUE.getLong(progress);
        } catch (IllegalAccessException e) {
            return 0L;
        }
    }

    static boolean setTaskProgressValue(TaskProgress progress, long value) {
        if (progress == null || TASK_PROGRESS_VALUE == null || value < 0L) {
            return false;
        }
        try {
            TASK_PROGRESS_VALUE.setLong(progress, value);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static Field taskProgressValueField() {
        try {
            Field field = TaskProgress.class.getDeclaredField("value");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            NeoECOAE.LOG.error("Unable to access AE2 crafting task progress; ECO batch path disabled", e);
            return null;
        }
    }

    private static long saturatingMultiply(long value, int multiplier) {
        if (value <= 0L || multiplier <= 0) {
            return 0L;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static int saturatingIntAdd(int left, int right) {
        return Integer.MAX_VALUE - left < right ? Integer.MAX_VALUE : left + right;
    }

    private final class BatchTransaction implements ECOCraftingBatchTransaction {

        private final appeng.api.networking.crafting.ICraftingPatternDetails details;
        private final InventoryCrafting table;
        private final TaskProgress progress;
        private final long originalTaskValue;
        private final int craftCount;
        private final double extraPower;
        private final List<IAEItemStack> extracted;
        private boolean finished;

        private BatchTransaction(appeng.api.networking.crafting.ICraftingPatternDetails details,
            InventoryCrafting table, TaskProgress progress, long originalTaskValue, int craftCount, double extraPower,
            List<IAEItemStack> extracted) {
            this.details = details;
            this.table = table;
            this.progress = progress;
            this.originalTaskValue = originalTaskValue;
            this.craftCount = craftCount;
            this.extraPower = extraPower;
            this.extracted = extracted;
        }

        @Override
        public int craftCount() {
            return this.craftCount;
        }

        @Override
        public void commit() {
            if (this.finished) {
                return;
            }
            this.finished = true;
            int extraCrafts = this.craftCount - 1;
            double charged = Double.NaN;
            boolean energyFailure = false;
            try {
                charged = ECOComputationVirtualCpu.this.batchEnergyGrid
                    .extractAEPower(this.extraPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            } catch (RuntimeException e) {
                energyFailure = true;
                NeoECOAE.LOG.error("ECO batch accepted but its crafting energy could not be charged", e);
            }
            if (!energyFailure && (Double.isNaN(charged) || charged < this.extraPower - 0.01D)) {
                NeoECOAE.LOG
                    .error("ECO batch accepted but energy charge was incomplete: {} / {}", charged, this.extraPower);
            }
            try {
                ECOComputationVirtualCpu.this.accountAdditionalOutputs(this.details, this.table, extraCrafts);
            } catch (RuntimeException e) {
                NeoECOAE.LOG.error("ECO batch accepted but its CPU accounting update failed", e);
            }
            ECOComputationVirtualCpu.this.craftsThisTick = saturatingIntAdd(
                ECOComputationVirtualCpu.this.craftsThisTick,
                this.craftCount);
            // AE2 decrements one normal operation after pushPattern returns. Verified batches
            // use the independent fast-path budget, matching the modern CPU scheduler.
            ECOComputationVirtualCpu.this.remainingOperations++;
            try {
                ECOComputationVirtualCpu.this.markDirty();
            } catch (RuntimeException e) {
                NeoECOAE.LOG.error("ECO batch accepted but its CPU state could not be marked dirty", e);
            }
        }

        @Override
        public void rollback() {
            if (this.finished) {
                return;
            }
            this.finished = true;
            RuntimeException failure = null;
            if (!setTaskProgressValue(this.progress, this.originalTaskValue)) {
                failure = new IllegalStateException("ECO CPU could not restore batch task progress");
            }
            try {
                ECOComputationVirtualCpu.this.rollbackInputs(this.extracted);
            } catch (RuntimeException e) {
                failure = appendFailure(failure, e);
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    @Override
    public void markDirty() {
        this.host.markDirty();
    }

    @Override
    public IGrid getGrid() {
        return this.grid;
    }

    @Override
    public boolean isActive() {
        return !this.released && this.active && this.host.isComputationCpuOnline();
    }

    @Override
    public boolean isBusy() {
        return this.cleanupPending || super.isBusy() || this.hasAllocatedJobState();
    }

    @Override
    public long getAvailableStorage() {
        return this.reservedStorage;
    }

    @Override
    public int getCoProcessors() {
        return this.coProcessors;
    }

    @Override
    public BaseActionSource getActionSource() {
        return this.machineSrc;
    }

    @Override
    public CraftingAllow getCraftingAllowMode() {
        return this.craftingAllowMode;
    }

    @Override
    public void changeCraftingAllowMode(CraftingAllow mode) {}

    @Override
    public String getName() {
        return this.myName;
    }

    @Override
    public Iterator<IGridHost> getTiles() {
        return Collections.<IGridHost>singleton(this.host)
            .iterator();
    }

    @Override
    public void updateStatus(boolean updateGrid) {}

    @Override
    public void updateName() {
        this.myName = this.pool.nameFor(this.serial, this.isBusy());
    }

    @Override
    public void breakCluster() {
        this.destroy();
    }

    @Override
    protected net.minecraft.world.World getWorld() {
        return this.host.getWorldObj();
    }

    @Override
    protected void done() {}

    private void releaseFromPool() {
        if (this.released) {
            return;
        }
        this.reservedForJob = false;
        this.cleanupPending = false;
        this.completionDeferred = false;
        this.released = true;
        this.active = false;
        this.pool.onCpuJobFinished(this);
    }

    private boolean hasAllocatedJobState() {
        return !this.released && this.reservedForJob && !this.isComplete;
    }

    private void beginCleanup() {
        this.cleanupPending = true;
        this.flushCleanup();
    }

    private void flushCleanup() {
        if (!this.cleanupPending) {
            return;
        }
        if (!this.inventory.isEmpty()) {
            this.storeItems();
        }
        if (this.inventory.isEmpty()) {
            this.releaseFromPool();
        }
    }

    private boolean hasRestoredJobState() {
        return !this.isComplete || super.isBusy()
            || this.getLastCraftingLink() != null
            || this.getUsedStorage() > 0L
            || !this.inventory.isEmpty();
    }

    private static CraftingAllow allowMode(ComputationCpuSelectionMode mode) {
        if (mode == ComputationCpuSelectionMode.PLAYER_ONLY) {
            return CraftingAllow.ONLY_PLAYER;
        }
        if (mode == ComputationCpuSelectionMode.MACHINE_ONLY) {
            return CraftingAllow.ONLY_NONPLAYER;
        }
        return CraftingAllow.ALLOW_ALL;
    }

    private static ComputationCpuSelectionMode cpuSelectionMode(CraftingAllow mode) {
        if (mode == CraftingAllow.ONLY_PLAYER) {
            return ComputationCpuSelectionMode.PLAYER_ONLY;
        }
        if (mode == CraftingAllow.ONLY_NONPLAYER) {
            return ComputationCpuSelectionMode.MACHINE_ONLY;
        }
        return ComputationCpuSelectionMode.ANY;
    }
}
