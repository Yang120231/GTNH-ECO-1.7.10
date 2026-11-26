package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Preconditions;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachments;
import cn.dancingsnow.neoecoae.api.me.completion.ECOVirtualCraftingCompletionSink;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingCpuContext;
import cn.dancingsnow.neoecoae.api.me.dispatch.ECOCraftingDispatchPolicyRegistry;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingLifecycle;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimResult;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimSink;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressSink;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOSingleCraftingExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingJobStatusPacket;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingLink;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingSubmitResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PlayerRegistry;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.TickClock;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

public class ECOCraftingCPULogic implements CraftingJobPersistenceContext, ECOCraftingProgressSink,
    ECOCraftingOutputClaimSink, ECOVirtualCraftingCompletionSink {

    final ECOCraftingCPU cpu;

    /**
     * 当前合成任务。
     */

    private ExecutingCraftingJob job = null;
    /**
     * 库存。
     */

    private final ListCraftingInventory inventory = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(
        ECOCraftingCPULogic.this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    private final ECOCraftingJobAttachments jobAttachments = new ECOCraftingJobAttachments();
    private final ECOProcessingMonitor processingMonitor = new ECOProcessingMonitor();
    /**
     * 如果 CPU 正在尝试清空库存但无法完成，则为 true。
     */

    private boolean cantStoreItems = false;
    final cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferQuarantine transfers = new cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferQuarantine();
    private boolean returningItemsToNetwork;

    private long lastModifiedOnTick = TickClock.currentTick();
    /** Bumped on every posted change or job swap so read-only view aggregates can be reused safely. */
    private long viewRevision;

    private boolean markedForDeletion = false;

    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting dispatchAccounting;
    private final ECOCraftingFastPathDispatcher fastPathDispatcher;
    private final ECOCraftingProviderDispatcher providerDispatcher;
    private final ECOCraftingTaskScheduler taskScheduler;
    private final ECOCraftingOutputDelivery outputDelivery;
    private final ECOCraftingCpuView view;
    private final ECOCraftingCpuPersistence persistence;
    private final ECOCraftingJobLifecycleController lifecycle;
    private final ECOExactRefillSchedule refillSchedule = new ECOExactRefillSchedule();
    private final ECOCraftingPlanUpdater planUpdater;
    private final ECOVanillaCraftingExecutor vanillaExecutor;
    final ECOBigOrderController bigOrder = new ECOBigOrderController(this);

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
        this.energyTransaction = new ECOCraftingEnergyTransaction(this::markCpuDirty, TickClock::currentTick);
        this.vanillaExecutor = new ECOVanillaCraftingExecutor(this, energyTransaction);
        this.dispatchAccounting = new ECOCraftingDispatchAccounting(
            this::postChange,
            this::markCpuDirty,
            this::createJobContext,
            event -> {
                processingMonitor.dispatched(event);
                ECOCraftingLifecycle.firePatternDispatched(event);
            });
        this.fastPathDispatcher = new ECOCraftingFastPathDispatcher(this, energyTransaction, dispatchAccounting);
        this.providerDispatcher = new ECOCraftingProviderDispatcher(
            this,
            fastPathDispatcher,
            energyTransaction,
            dispatchAccounting);
        this.taskScheduler = new ECOCraftingTaskScheduler(providerDispatcher);
        this.outputDelivery = new ECOCraftingOutputDelivery(this);
        this.view = new ECOCraftingCpuView(this);
        this.persistence = new ECOCraftingCpuPersistence(this);
        this.lifecycle = new ECOCraftingJobLifecycleController(this);
        this.planUpdater = new ECOCraftingPlanUpdater(this);
    }

    public ICraftingSubmitResult trySubmitJob(GridView grid, ICraftingPlan plan, IActionSource src,
        @Nullable ICraftingRequester requester) {
        return lifecycle.submit(grid, plan, src, requester);
    }

    /**
     * GTNH {@code CraftingCPUCluster#mergeJob}: a player's AE2-planned request for the item this CPU is already
     * crafting joins the running vanilla-executed job. ECO-planned jobs carry a fixed phase schedule and never merge.
     *
     * @return the running job's link on success, null when this CPU cannot merge the plan
     */
    @Nullable
    public ICraftingLink tryMergeVanillaJob(GridView grid,
        cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan plan, IActionSource src) {
        var current = job;
        if (current == null || grid == null
            || !plan.vanillaExecution()
            || !current.vanillaExecution
            || current.suspended
            || current.permanentExecutionError != null
            || current.exactOrder
            || bigOrder.progress() != null
            || !current.link.isStandalone()
            || current.link.isCanceled()
            || current.finalOutput == null
            || !current.finalOutput.what()
                .equals(
                    plan.finalOutput()
                        .what())
            || !cpu.isActive()) return null;
        var previous = cpu.getPlan();
        if (previous == null) return null;
        var merged = new cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan(
            new GenericStack(
                current.finalOutput.what(),
                cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingAdd(
                    previous.finalOutput()
                        .amount(),
                    plan.finalOutput()
                        .amount())),
            cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingAdd(previous.bytes(), plan.bytes()),
            previous.simulation(),
            previous.multiplePaths(),
            new KeyCounter(),
            new KeyCounter(),
            new KeyCounter(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf()).markVanillaExecution();
        if (merged.bytes() - previous.bytes() > cpu.getCluster()
            .getAvailableStorage()) return null;

        // Stage into a scratch inventory: a failed extraction must roll back only this request, never the running
        // job's materials (native mergeJob restores its backup inventory for the same reason).
        var staged = new ListCraftingInventory(ignored -> {});
        if (cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper
            .tryExtractInitialItems(plan, grid, staged, src) != null) {
            // Whatever the rollback could not put back into the network stays in this CPU's custody.
            boolean kept = false;
            for (var entry : staged.list) {
                if (entry.getLongValue() <= 0) continue;
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                kept = true;
            }
            if (kept) markCpuDirty();
            return null;
        }
        if (!cpu.getCluster()
            .replaceMergedPlan(cpu, merged)) {
            var storage = grid.getStorageService()
                .getInventory();
            for (var entry : staged.list) {
                long returned = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, src);
                if (returned < entry.getLongValue())
                    inventory.insert(entry.getKey(), entry.getLongValue() - returned, Actionable.MODULATE);
            }
            return null;
        }
        for (var entry : staged.list) {
            if (entry.getLongValue() > 0) inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        }
        for (var entry : plan.generatedAtStart())
            inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        // The vanilla executor re-reads job.tasks every tick and keeps its native three-tick operation window.
        current.mergeVanillaPlan(plan);
        if (nativeCPU() instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu bridge)
            bridge.trackRequester(src.nativeSource());
        var touched = new KeyCounter();
        getAllItems(touched);
        for (var entry : touched) postChange(entry.getKey());
        postChange(current.finalOutput.what());
        markCpuDirty();
        return current.link;
    }

    void tickFromGrid(cn.dancingsnow.neoecoae.platform.ae2.grid.GridView grid) {
        tickCraftingLogic(grid.getEnergyService(), null, grid);
    }

    public void tickCraftingLogic(IEnergyGrid eg, ICraftingService cc) {
        tickCraftingLogic(eg, cc, null);
    }

    private void tickCraftingLogic(IEnergyGrid eg, ICraftingService cc,
        cn.dancingsnow.neoecoae.platform.ae2.grid.GridView grid) {
        if (job != null && ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), job.link.getCraftingID())) {
            cancel();
            return;
        }
        if (!cpu.isActive()) {
            return;
        }
        if (bigOrder.tick()) return;
        cantStoreItems = false;
        if (this.job == null) {
            energyTransaction.returnIdleCredit(eg);
            this.storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            } else {
                if (markedForDeletion && transfers.isEmpty()) {
                    cpu.deactivate();
                }
            }
            return;
        }
        if (job.link.isCanceled()) {
            cancel();
            return;
        }

        long currentTick = TickClock.currentTick();
        if (!job.suspended) refillExactMaterials(currentTick);

        deliverStoredFinalOutput();
        if (job == null || job.suspended || outputDelivery.hasUncertainFinalDelivery()) {
            return;
        }
        if (job.vanillaExecution) {
            // AE2-planned job: behave exactly like a native CPU with this host's co-processors. ECO dispatch
            // policies, plan improvements and the ECO scheduler never see it.
            tickVanillaCrafting(eg, grid == null ? cc : grid.getCraftingService());
            return;
        }
        planUpdater.tick(currentTick);
        World level = cpu.getLevel();
        if (level == null) {
            return;
        }

        if (!ECOCraftingDispatchPolicyRegistry.mayTick(createCpuContext(currentTick))) return;

        if (grid != null) cc = grid.getCraftingService();
        var budget = taskScheduler.budgetForTick(currentTick);
        var current = job;
        // Keep the actual executeCrafting call for Thunderbolt. A spent single-copy allowance does not stop batches.
        while (current != null && job == current && !current.suspended && budget.hasTime()) {
            if (executeCrafting(budget.remainingOrdinary(), cc, eg, level) == 0) break;
        }
    }

    private void tickVanillaCrafting(IEnergyGrid eg, ICraftingService craftingService) {
        World level = cpu.getLevel();
        var grid = cpu.getGrid();
        var nativeGrid = grid == null ? null : grid.nativeGrid();
        var cache = nativeGrid == null ? null : nativeGrid.getCache(appeng.api.networking.crafting.ICraftingGrid.class);
        if (level == null || eg == null || !(cache instanceof appeng.me.cache.CraftingGridCache)) return;
        vanillaExecutor.tick(
            job,
            eg,
            (appeng.me.cache.CraftingGridCache) cache,
            level,
            Math.max(0, cpu.getCoProcessors()),
            craftingService);
    }

    /** 从所有配方输入共用的实际库存中重试交付产物。 */
    private void deliverStoredFinalOutput() {
        outputDelivery.deliverStoredFinalOutput();
    }

    private java.util.List<ICraftingProvider> collectAvailableProviders(ICraftingService craftingService,
        IPatternDetails details) {
        // 此处必须且只能保留一次 getProviders 调用；仅在 CPU 中提供转发桩方法不足以满足兼容要求。
        return craftingService.getProviders(details);
    }

    /**
     * 尝试将样板推送到可用接口中，即执行实际的合成操作。
     *
     * @param maxPatterns 剩余可接受的普通推送次数；已验证的批量操作不消耗此额度。
     * @return 成功推送的样板数量。
     */
    public int executeCrafting(int maxPatterns, ICraftingService craftingService, IEnergyGrid energyService,
        World level) {
        // Mixin 二进制兼容锚点：Thunderbolt 会包装本方法中的这一调用，必须保留其调用位置。
        // 实际调度实现拆分到下方各策略对应的方法中；此处的守卫条件在运行时刻意保持为假。
        // 这里仅用于提供兼容锚点，绝不会访问合成提供者。

        return executeNormalCrafting(maxPatterns, craftingService, energyService, level);
    }

    /** 执行一轮调度。 */
    public int executeNormalCrafting(int maxPatterns, ICraftingService craftingService, IEnergyGrid energyService,
        World level) {
        var current = job;
        if (current == null) return 0;
        return taskScheduler
            .execute(maxPatterns, craftingService, energyService, level, current, inventory, pattern -> {
                var providers = collectAvailableProviders(craftingService, pattern);
                current.recordProcessingProviderAvailability(pattern, !providers.isEmpty());
                return providers;
            },
                () -> job == current && !current.suspended,
                createCpuContext(TickClock.currentTick()),
                this::invokeNormalProvider);
    }

    /**
     * 普通合成提供者调用的兼容适配器。候选项和提供者的顺序由调度器管理；
     * 此处将各提供者特有的 AE2、Thunderbolt 和 Useless 调用行为保留在所属 CPU 中。
     */
    private boolean invokeNormalProvider(ECOCraftingDispatchRequest request, ICraftingProvider provider) {
        if (provider instanceof ECOCraftingPatternBus) {
            return ECOSingleCraftingExecutor.pushPattern(
                provider,
                request.pattern(),
                request.inputs(),
                request.outputs(),
                request.remainders(),
                request.level(),
                request.job().link.getCraftingID());
        }

        var parallelProvider = ECOParallelCraftingProviders.find(provider);
        if (parallelProvider != null) {
            // An adapter can appear after eligibility was checked. Never label scaled totals as one craft.
            if (request.pattern() instanceof ECOProcessingExecutionPattern) return false;
            return parallelProvider
                .eco$pushPatternBatch(request.pattern(), request.inputs(), 1L, request.job().link.getCraftingID());
        }

        // 兼容性约定：
        // 必须在 ECOCraftingCPULogic 中原样保留此 ICraftingProvider.pushPattern 调用。
        // Useless 和 Thunderbolt 的集成可能针对其所属类、方法描述符或调用点进行包装。
        return provider.pushPattern(request.pattern(), request.inputs());
    }

    /** 将尚待接收的产物存入 CPU 自有的实际库存。 */
    public long insert(AEKey what, long amount, Actionable type) {
        var current = job;
        if (current == null || what == null || amount <= 0L) {
            return outputDelivery.insert(what, amount, type);
        }

        // AE2 Utility 通过此方法中的真实调用点重定向带 NBT 物品的等待库存匹配；
        // 结果继续交给输出交付组件，避免破坏当前的任务校验、记账和完成流程。
        long accepted = current.waitingFor.extract(what, amount, Actionable.SIMULATE);

        // AE2 Utility 还会重定向此方法中的真实 matches 调用，用于判断最终产物交付阶段。
        boolean matchesFinalOutput = current.finalOutput != null && what.matches(current.finalOutput);
        long strict = outputDelivery.insert(what, amount, type, accepted, matchesFinalOutput);
        if (job != current || strict >= amount) return strict;
        return strict;
    }

    /**
     * 仅在此 CPU 仍持有所指定的合成任务时接收工作单元的产物，并将多余产物保留在本地。
     *
     * <p>
     * 工作单元的产物携带任务标识，但 AE2 的旧版 {@code insertIntoCpus} 接口不携带该信息。
     * 在 CPU 边界保留此检查，可防止产物被分配给恰好也在等待相同资源键的其他 CPU。
     * </p>
     */
    public long insertForJob(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        return outputDelivery.insertForJob(craftingJobId, what, amount, type);
    }

    public java.math.BigInteger insertExactForJob(UUID jobId, AEKey key, java.math.BigInteger amount, Actionable mode) {
        return outputDelivery.insertExactForJob(jobId, key, amount, mode);
    }

    /**
     * Claims an output when the machine's actual key is not necessarily the key reserved by the plan.
     * Confirmed remainders stay in inventory. An offer without a receipt is not part of the refundable balance.
     */
    @Override
    public ECOCraftingOutputClaimResult claimCraftingOutput(ECOCraftingOutputClaimRequest request) {
        return outputDelivery.claimCraftingOutput(request);
    }

    /**
     * Completes a virtual execution while retaining ECO's task, phase and physical-output guards.
     */
    @Override
    public boolean tryCompleteVirtualCrafting(IPatternDetails pattern, long completedCrafts) {
        var current = job;
        if (current == null || pattern == null
            || outputDelivery.hasUncertainFinalDelivery()
            || outputDelivery.isDeliveringFinalOutput()
            || completedCrafts <= 0L
            || ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), current.link.getCraftingID())) {
            return false;
        }

        Map.Entry<IPatternDetails, ECOExecutionTaskProgress> taskEntry;
        try {
            taskEntry = current.tasks.entrySet()
                .stream()
                .filter(entry -> entry.getKey() == pattern || ECOPhaseScheduler.samePattern(entry.getKey(), pattern))
                .findFirst()
                .orElse(null);
        } catch (RuntimeException rejected) {
            return false;
        }
        if (taskEntry == null || taskEntry.getValue() == null || taskEntry.getValue().value < completedCrafts) {
            return false;
        }

        if (current.executionRuntime != null) {
            ECOExecutionRuntime.DispatchCandidate candidate;
            try {
                candidate = current.executionRuntime.candidates()
                    .stream()
                    .filter(value -> ECOPhaseScheduler.samePattern(value.pattern(), pattern))
                    .findFirst()
                    .orElse(null);
            } catch (RuntimeException rejected) {
                return false;
            }
            if (candidate == null || completedCrafts > candidate.maxDispatchCount()) {
                return false;
            }
            try {
                current.executionRuntime.onVirtualAccepted(candidate, completedCrafts);
            } catch (RuntimeException rejected) {
                return false;
            }
        } else {
            taskEntry.getValue()
                .accept(completedCrafts);
        }

        markCpuDirty();

        if (!taskScheduler.hasPendingTasks(current) && current.waitingFor.list.isEmpty()
            && (current.executionRuntime == null || current.executionRuntime.isComplete())) {
            // A virtual provider has already accounted for the requested final output; no physical output callback
            // will arrive to decrement this field later.
            current.remainingAmount = 0L;
            finishJob(true);
        }
        return true;
    }

    public boolean hasCraftingJob(UUID craftingJobId) {
        return craftingJobId != null && job != null && craftingJobId.equals(job.link.getCraftingID());
    }

    /**
     * 完成当前合成任务。
     *
     * @param success 任务完成则为 true，取消则为 false。
     */
    protected void finishJob(boolean success) {
        lifecycle.finish(success);
    }

    /**
     * 取消当前合成任务。
     */
    public void cancel() {
        lifecycle.cancel();
    }

    /**
     * 尝试将所有本地存储的物品转存回存储网络。
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        if (returningItemsToNetwork || outputDelivery.isDeliveringFinalOutput()) return;
        returningItemsToNetwork = true;
        try {
            returnItemsToNetwork();
        } finally {
            returningItemsToNetwork = false;
        }
    }

    private void returnItemsToNetwork() {
        if (this.inventory.list.isEmpty()) return;

        var g = cpu.getGrid();
        if (g == null) return;

        var storage = g.getStorageService()
            .getInventory();

        var source = cpu.getActionSource();
        for (var entry : exactInventory().snapshot()
            .entrySet()) {
            var key = entry.getKey();
            var offered = cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage
                .insertionWindow(storage, entry.getValue());
            var accepted = transfers.attemptExact(
                key,
                offered,
                () -> cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage
                    .insert(storage, key, offered, Actionable.MODULATE, source));
            settleTransferredInventory(key, accepted == null ? offered : accepted);
            if (accepted == null) break;
        }
    }

    void settleTransferredInventory(AEKey key, java.math.BigInteger unavailable) {
        if (unavailable.signum() <= 0) return;
        try {
            exactInventory().settleTransfer(key, unavailable);
        } finally {
            markCpuDirty();
        }
    }

    void postChange(@Nullable AEKey what) {
        taskScheduler.invalidateInputTemplates(what);
        lastModifiedOnTick = TickClock.currentTick();
        viewRevision++;
        notifyListeners(what);
    }

    private void notifyListeners(@Nullable AEKey what) {
        for (var listener : listeners) listener.accept(what);
    }

    void markCpuDirty() {
        cpu.markDirty();
    }

    ECOCraftingTaskScheduler taskSchedulerForOutput() {
        return taskScheduler;
    }

    ECOCraftingTaskScheduler taskSchedulerForPersistence() {
        return taskScheduler;
    }

    ECOCraftingTaskScheduler taskSchedulerForLifecycle() {
        return taskScheduler;
    }

    ECOCraftingJobAttachments jobAttachmentsForPersistence() {
        return jobAttachments;
    }

    ECOCraftingJobAttachments jobAttachmentsForLifecycle() {
        return jobAttachments;
    }

    /** The running job's patterns that output {@code key}, primary outputs first; empty without a job. */
    public java.util.List<IPatternDetails> patternsProducing(AEKey key) {
        var current = job;
        if (current == null || key == null) return java.util.Collections.emptyList();
        var primary = new java.util.ArrayList<IPatternDetails>();
        var secondary = new java.util.ArrayList<IPatternDetails>();
        for (var pattern : current.tasks.keySet()) {
            if (key.equals(
                pattern.getPrimaryOutput()
                    .what()))
                primary.add(pattern);
            else for (var output : pattern.getOutputs()) if (key.equals(output.what())) {
                secondary.add(pattern);
                break;
            }
        }
        primary.addAll(secondary);
        return primary;
    }

    /** The running job's crafting id, or null without a job. */
    @Nullable
    public UUID runningJobId() {
        var current = job;
        return current == null ? null : current.link.getCraftingID();
    }

    public ECOProcessingMonitor getProcessingMonitor() {
        return processingMonitor;
    }

    @Nullable
    public UUID getProcessingJobId() {
        return job == null ? null : job.link.getCraftingID();
    }

    public java.util.List<IPatternDetails> getProcessingPatterns(AEKey output) {
        if (job == null) return java.util.Collections.emptyList();
        return job.tasks.keySet()
            .stream()
            .filter(
                pattern -> output.equals(
                    pattern.getPrimaryOutput()
                        .what()))
            .collect(java.util.stream.Collectors.toList());
    }

    public boolean isProcessingStepStopped(AEKey output) {
        return job != null && job.isProcessingOutputStopped(output);
    }

    public boolean canControlProcessingStep(AEKey output) {
        // A stopped key stays controllable so it can always be resumed, even if no pattern is left for it.
        return job != null && (job.isProcessingOutputStopped(output) || getProcessingPatterns(output).stream()
            .anyMatch(pattern -> job.tasks.get(pattern).value > 0));
    }

    /** Stop only future admission. Accepted batches and expected outputs retain their original ownership. */
    public boolean setProcessingStepStopped(AEKey output, boolean stopped) {
        if (!canControlProcessingStep(output)) return false;
        // Resume only this step; other outputs the player stopped separately stay stopped.
        boolean changed = stopped ? job.stoppedProcessingOutputs.add(output)
            : job.stoppedProcessingOutputs.remove(output);
        if (changed) {
            taskScheduler.reset();
            markCpuDirty();
            for (var pattern : getProcessingPatterns(output))
                for (var result : pattern.getOutputs()) postChange(result.what());
        }
        return changed;
    }

    public void refreshProcessingProgress() {
        var world = cpu.getLevel();
        if (job != null && world != null) processingMonitor.advance(world, world.getTotalWorldTime());
    }

    public cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress getProcessingProgress(
        AEKey output) {
        return getProcessingProgress(output, false);
    }

    public cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress getProcessingProgress(AEKey output,
        boolean priority) {
        var world = cpu.getLevel();
        var current = job;
        if (current == null || output == null || world == null)
            return cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.EMPTY;
        if (getWaitingFor(output) <= 0 && getPendingOutputs(output) <= 0)
            return cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.EMPTY;
        var sampled = processingMonitor.sample(world, output, world.getTotalWorldTime(), priority);
        java.math.BigInteger total;
        java.math.BigInteger completed;
        if (current.finalOutput != null && output.equals(current.finalOutput.what())) {
            total = java.math.BigInteger.valueOf(current.finalOutput.amount());
            completed = total.subtract(java.math.BigInteger.valueOf(Math.max(0L, current.remainingAmount)));
        } else {
            total = current.planTotals.get(output);
            completed = current.producedTotals.getOrDefault(output, java.math.BigInteger.ZERO);
        }
        if (total == null || total.signum() <= 0) return sampled;
        if (completed.signum() <= 0) return sampled.withGlobalProgress(0);
        if (completed.compareTo(total) >= 0) return sampled.withGlobalProgress(1);
        float fraction = new java.math.BigDecimal(completed)
            .divide(new java.math.BigDecimal(total), java.math.MathContext.DECIMAL64)
            .floatValue();
        // Conversion to a display float must not round an incomplete exact order up to 100%.
        return sampled.withGlobalProgress(Math.min(Math.nextDown(1.0F), fraction));
    }

    ECOCraftingEnergyTransaction energyTransactionForPersistence() {
        return energyTransaction;
    }

    ECOCraftingOutputDelivery outputDeliveryForPersistence() {
        return outputDelivery;
    }

    void setJobFromPersistence(@Nullable ExecutingCraftingJob restoredJob) {
        releaseExactRefill();
        planUpdater.reset();
        this.job = restoredJob;
        viewRevision++;
        exactInventory().setEnabled(restoredJob != null && restoredJob.exactOrder);
    }

    void setJobFromLifecycle(@Nullable ExecutingCraftingJob nextJob) {
        releaseExactRefill();
        planUpdater.reset();
        vanillaExecutor.reset();
        this.job = nextJob;
        viewRevision++;
        exactInventory().setEnabled(nextJob != null && nextJob.exactOrder);
    }

    public cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory exactInventory() {
        return (cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) inventory;
    }

    public boolean hasJob() {
        return view.hasJob();
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return view.getFinalJobOutput();
    }

    public long getRemainingJobOutputAmount() {
        return view.getRemainingJobOutputAmount();
    }

    /** Production has ended, but the destination did not accept all of the final output. */
    public boolean isFinalOutputDeliveryBlocked() {
        return outputDelivery.isFinalOutputDeliveryBlocked();
    }

    public void collectBlockedFinalOutputs(KeyCounter output) {
        outputDelivery.collectBlockedFinalOutputs(output);
    }

    public boolean hasUnavailableProcessingPattern() {
        return job != null && job.hasUnavailableProcessingPattern();
    }

    public String getSingleProcessingReason() {
        return job == null ? "" : job.singleProcessingReason;
    }

    public long getDispatchCopyLimit() {
        return job == null || job.vanillaExecution ? Long.MAX_VALUE : providerDispatcher.dispatchLimit();
    }

    /**
     * @deprecated Use {@link #getProgressView()} so integrations cannot mutate or depend on the tracker type.
     */
    @Deprecated
    public ElapsedTimeTracker getElapsedTimeTracker() {
        return view.getElapsedTimeTracker();
    }

    /** Returns a detached progress snapshot for external displays and integrations. */
    public ECOCraftingProgressView getProgressView() {
        var base = view.getProgressView();
        var parent = bigOrder.progress();
        if (parent == null) return base;
        return new ECOCraftingProgressView() {

            public float progress() {
                return base.progress();
            }

            public long elapsedTimeNanos() {
                return base.elapsedTimeNanos();
            }

            public long startedWork(AEKeyType type) {
                return base.startedWork(type);
            }

            public long completedWork(AEKeyType type) {
                return base.completedWork(type);
            }

            public java.util.Optional<cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress> bigOrder() {
                return java.util.Optional.of(parent);
            }
        };
    }

    @Override
    public void recordCompletedCraftingWork(long amount, AEKeyType keyType) {
        Preconditions.checkArgument(amount >= 0, "Completed crafting work must not be negative");
        Preconditions.checkNotNull(keyType, "keyType");
        if (this.job != null && amount != 0) {
            this.job.timeTracker.decrementItems(amount, keyType);
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        persistence.read(data, registries);
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        persistence.write(data, registries);
    }

    void loadJobAttachments(CompoundTag jobData, HolderLookup.Provider registries) {
        if (job != null) jobAttachments.load(jobData, registries, createJobContext(job));
    }

    void writeJobAttachments(CompoundTag jobData, HolderLookup.Provider registries) {
        if (job != null) jobAttachments.save(jobData, registries, createJobContext(job));
    }

    public ICraftingLink getLastLink() {
        if (this.job != null) {
            return this.job.link;
        }
        return null;
    }

    /**
     * 注册一个监听器，当存储物品、等待物品或待处理输出发生变化时接收通知。
     * 仅供菜单使用。务必通过 {@link #removeListener} 来移除。
     */
    public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener);
    }

    public long getStored(AEKey template) {
        return view.getStored(template);
    }

    public long getWaitingFor(AEKey template) {
        return view.getWaitingFor(template);
    }

    /** Whether this output has actually used a multi-craft dispatch in the current job. */
    public boolean isBatchedOutput(AEKey template) {
        return view.isBatchedOutput(template);
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        view.getAllWaitingFor(waitingFor);
    }

    public long getPendingOutputs(AEKey template) {
        return view.getPendingOutputs(template);
    }

    /** Adds every positive pending output in one pass. */
    public void collectPendingOutputs(KeyCounter out) {
        view.collectPendingOutputs(out);
    }

    /** Single-key equivalent of {@link #getAllItems(KeyCounter)}. */
    public long getAllAmount(AEKey template) {
        return view.getAllAmount(template);
    }

    long viewRevision() {
        return viewRevision;
    }

    public java.util.Map<AEKey, java.math.BigInteger> getExactPendingPreview() {
        if (job != null && job.exactOrder) {
            var amounts = new java.util.HashMap<AEKey, java.math.BigInteger>();
            job.tasks.forEach(
                (pattern, progress) -> pattern.getOutputs()
                    .forEach(
                        output -> amounts.merge(
                            output.what(),
                            progress.remainingExact()
                                .multiply(java.math.BigInteger.valueOf(output.amount())),
                            java.math.BigInteger::add)));
            job.deferredEmitted.forEach((key, amount) -> amounts.merge(key, amount, java.math.BigInteger::add));
            return amounts;
        }
        return bigOrder.exactPendingPreview();
    }

    public java.util.Map<AEKey, java.math.BigInteger> getExactStoredPreview() {
        return job != null && job.exactOrder
            ? ((cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) inventory).snapshot()
            : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf();
    }

    public java.util.Map<AEKey, java.math.BigInteger> getExactActivePreview() {
        return job != null && job.exactOrder
            ? ((cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) job.waitingFor).snapshot()
            : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf();
    }

    /** Refill physical buffers within one job, without creating or replanning child orders. */
    private void refillExactMaterials(long currentTick) {
        if (!job.exactOrder) return;
        var grid = cpu.getGrid();
        refillSchedule.clearIfGridChanged(grid);
        if (grid == null || (job.deferredStock.isEmpty() && job.deferredEmitted.isEmpty())) return;
        if (!job.deferredStock.isEmpty()) {
            refillSchedule.bind(grid, job.deferredStock.keySet(), currentTick);
            if (!refillSchedule.hasDue(currentTick) && job.deferredEmitted.isEmpty()) return;
        }
        var source = cpu.getActionSource();
        if (job.playerId != null) {
            var level = cpu.getLevel();
            var player = level == null ? null
                : cn.dancingsnow.neoecoae.platform.ae2.crafting.PlayerRegistry.getConnected(
                    cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level),
                    job.playerId);
            if (player == null) return;
            source = cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource.ofPlayer(
                player,
                source.machine()
                    .orElse(null));
        }
        AEKey key;
        while ((key = refillSchedule.poll(currentTick)) != null) {
            var remaining = job.deferredStock.get(key);
            var generated = cn.dancingsnow.neoecoae.impl.storage.ECOCreativeSupply
                .extract(grid, key, remaining, source);
            if (generated.signum() > 0) {
                exactInventory().restore(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, generated));
            } else {
                long wanted = cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(remaining);
                long extracted = grid.getStorageService()
                    .getInventory()
                    .extract(key, wanted, appeng.api.config.Actionable.MODULATE, source);
                if (extracted > 0) {
                    inventory.insert(key, extracted, appeng.api.config.Actionable.MODULATE);
                    generated = java.math.BigInteger.valueOf(extracted);
                }
            }
            boolean progress = generated.signum() > 0;
            if (progress) {
                remaining = remaining.subtract(generated);
                if (remaining.signum() == 0) job.deferredStock.remove(key);
                else job.deferredStock.put(key, remaining);
                taskScheduler.recordPhysicalInsert(key);
                postChange(key);
                markCpuDirty();
            }
            if (remaining.signum() > 0) refillSchedule.retry(key, currentTick, progress);
        }
        if (job.deferredStock.isEmpty()) releaseExactRefill();
        for (var entry : job.deferredEmitted.entrySet()) {
            ((cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory) job.waitingFor)
                .restore(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(entry.getKey(), entry.getValue()));
            entry.setValue(java.math.BigInteger.ZERO);
            postChange(entry.getKey());
            markCpuDirty();
        }
        job.deferredEmitted.values()
            .removeIf(amount -> amount.signum() == 0);
    }

    public void releaseExactRefill() {
        refillSchedule.clear();
    }

    public void refreshExactRefillGrid() {
        refillSchedule.clearIfGridChanged(cpu.getGrid());
    }

    /**
     * 供菜单使用，收集所有类型的存储物品。
     */
    public void getAllItems(KeyCounter out) {
        view.getAllItems(out);
    }

    /** 仅收集此 CPU 实际持有的物品，不包括计划产物和尚未返回的产物。 */
    public void getOwnedItems(KeyCounter out) {
        view.getOwnedItems(out);
    }

    /** {@link #getOwnedItems(KeyCounter)} 对应的无内存分配检查；必须覆盖相同的库存记录。 */
    public boolean hasOwnedItems() {
        return view.hasOwnedItems();
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    /** 供 CPU 菜单和集成使用的稳定诊断接口；返回 null 表示任务仍可执行。 */
    public @Nullable String getPermanentExecutionError() {
        return job == null ? null : job.permanentExecutionError;
    }

    public void setJobSuspended(boolean suspended) {
        if (!suspended && job != null && job.permanentExecutionError != null) {
            return;
        }
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            markCpuDirty();
        }
    }

    void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickClock.currentTick();

        var playerId = job.playerId;
        if (playerId == null || job.finalOutput == null) {
            return;
        }

        World level = cpu.getLevel();
        if (level == null) {
            return;
        }

        var server = cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level);
        var connectedPlayer = PlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            CraftingJobStatusPacket.send(connectedPlayer, jobId, job.finalOutput, job.remainingAmount, status);
        }
    }

    ECOCraftingJobContext createJobContext(ExecutingCraftingJob target) {
        GenericStack output = target.finalOutput;
        long requested = output == null ? 0L : Math.max(0L, output.amount());
        return new ECOCraftingJobContext(
            cpu,
            target.link.getCraftingID(),
            output,
            requested,
            Math.max(0L, target.remainingAmount));
    }

    private ECOCraftingCpuContext createCpuContext(long gameTick) {
        UUID craftingJobId = job == null ? null : job.link.getCraftingID();
        return new ECOCraftingCpuContext(
            cpu,
            craftingJobId,
            cpu.isActive(),
            Math.max(0, cpu.getCoProcessors()),
            gameTick);
    }

    public void markForDeletion() {
        this.markedForDeletion = true;
    }

    public World getLevel() {
        return cpu.getLevel();
    }

    public appeng.api.networking.crafting.ICraftingCPU nativeCPU() {
        return cpu.nativeCPU();
    }

    public ExecutingCraftingJob getJob() {
        return job;
    }

    public ListCraftingInventory getInventory() {
        return inventory;
    }

    public boolean isCantStoreItems() {
        return cantStoreItems;
    }

    public long getLastModifiedOnTick() {
        return lastModifiedOnTick;
    }

    public boolean isMarkedForDeletion() {
        return markedForDeletion;
    }
}
