package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingRequester;
import appeng.core.AELog;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingLifecycle;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingJobStatusPacket;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingSubmitResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PlayerRegistry;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;

/** Coordinates CPU job ownership, attachment lifetime and terminal transitions. */
final class ECOCraftingJobLifecycleController {

    private final ECOCraftingCPULogic host;

    ECOCraftingJobLifecycleController(ECOCraftingCPULogic host) {
        this.host = host;
    }

    ICraftingSubmitResult submit(GridView grid, ICraftingPlan plan, IActionSource src,
        @Nullable ICraftingRequester requester) {
        if (host.getJob() != null) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (!host.cpu.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (host.cpu.getAvailableStorage() < plan.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }

        if (!host.getInventory().list.isEmpty() && host.exactInventory()
            .isEnabled()) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (!host.getInventory().list.isEmpty()) {
            AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");
        }

        var attachedPlanningResult = (Object) plan instanceof ECOCraftingPlanDiagnostics diagnostics
            ? diagnostics.neoecoae$getPlanningResult()
            : null;
        if (attachedPlanningResult == null) attachedPlanningResult = ECOPlanningResultRegistry.find(plan);
        var contract = ECOPlanningResultRegistry.resolveContract(plan, attachedPlanningResult);
        var executionPlan = contract == null ? ECOPlanningResultRegistry.resolveExecutionPlan(plan)
            : contract.executionPlan();
        if (plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan exact)
            executionPlan = exact.execution();

        // Imported automatic wrappers must be validated before taking network materials.

        host.exactInventory()
            .setEnabled(plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan);
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, host.getInventory(), src);
        if (missingIngredient != null) {
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        }

        var playerId = src.player()
            .map(
                player -> player instanceof EntityPlayerMP serverPlayer ? PlayerRegistry.getPlayerId(serverPlayer)
                    : null)
            .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(
            CraftingCpuHelper.generateLinkData(craftId, requester == null, false),
            host.nativeCPU());
        var job = new ExecutingCraftingJob(plan, executionPlan, host::postChange, linkCpu, playerId);
        if (contract != null) job.fuzzyPlanningItemIds = contract.fuzzyPlanningItemIds();
        if (contract != null && attachedPlanningResult != null
            && contract.planningId()
                .equals(attachedPlanningResult.planningId())) {
            job.improvements = attachedPlanningResult.improvements();
            if (job.improvements != null && !job.improvements.belongsTo(grid.getCraftingService()))
                job.improvements = null;
            if (job.improvements != null) job.improvements.close();
        }
        if (plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan exact)
            job.fuzzyPlanningItemIds = exact.fuzzyPlanningItemIds();
        host.setJobFromLifecycle(job);

        var admission = cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest.forSubmission(plan);
        if (admission != null) {
            host.bigOrder.start(admission, src);
        }
        host.outputDeliveryForPersistence()
            .clearPendingFinalOutputs();
        initializeJobAttachments();

        var taskScheduler = host.taskSchedulerForLifecycle();
        taskScheduler.resetDispatchState();
        taskScheduler.bindDiagnostics(craftId, cn.dancingsnow.neoecoae.platform.minecraft.TickClock.currentTick());
        // Publish planned outputs immediately so AE2 can display and cancel the new job before the first machine tick.
        var initialStatusItems = new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter();
        host.getAllItems(initialStatusItems);
        for (var entry : initialStatusItems) {
            host.postChange(entry.getKey());
        }

        host.markCpuDirty();
        host.notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        if (requester != null) {
            var linkReq = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);
            var craftingService = (ICraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);
            ECOCraftingLifecycle.fireJobStarted(host.createJobContext(job));
            return CraftingSubmitResult.successful(linkReq);
        }

        ECOCraftingLifecycle.fireJobStarted(host.createJobContext(job));
        return CraftingSubmitResult.successful(null);
    }

    void finish(boolean success) {
        var finishingJob = host.getJob();
        if (finishingJob == null) {
            return;
        }
        if (success && host.bigOrder.finishChild()) return;
        var context = host.createJobContext(finishingJob);
        long remainingAmount = Math.max(0L, finishingJob.remainingAmount);
        long completedAmount = Math.max(0L, context.requestedAmount() - remainingAmount);
        var result = new ECOCraftingJobResult(
            success ? ECOCraftingJobResult.Status.SUCCESS : ECOCraftingJobResult.Status.CANCELLED,
            context.requestedAmount(),
            completedAmount,
            remainingAmount);

        ECOCraftingJobLifecycle.finish(host.cpu.getLevel(), finishingJob.link.getCraftingID(), success);
        if (success) {
            finishingJob.link.markDone();
            ECOCraftingWorkerRecovery.releaseCompletedOutputs(host.cpu.getGrid(), finishingJob.link.getCraftingID());
        } else {
            finishingJob.link.cancel();
        }

        finishingJob.waitingFor.clear();
        for (var entry : finishingJob.tasks.entrySet()) {
            for (var output : entry.getKey()
                .getOutputs()) {
                host.postChange(output.what());
            }
        }

        host.notifyJobOwner(
            finishingJob,
            success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);
        clearJobAttachments(result);
        host.setJobFromLifecycle(null);
        host.taskSchedulerForLifecycle()
            .reset();
        host.storeItems();
        host.outputDeliveryForPersistence()
            .clearPendingFinalOutputs();
        ECOCraftingLifecycle.fireJobFinished(context, result);
        host.bigOrder.clear();
    }

    void cancel() {
        var current = host.getJob();
        if (current == null) {
            return;
        }
        UUID craftingJobId = current.link.getCraftingID();
        if (host.bigOrder.progress() != null) {
            host.bigOrder.cancel();
            ECOCraftingJobLifecycle.finish(host.cpu.getLevel(), craftingJobId, false);
            ECOCraftingWorkerRecovery.recoverTerminatedInputs(host.cpu.getLevel(), craftingJobId);
            finish(false);
        } else {
            finish(false);
            ECOCraftingWorkerRecovery.recoverTerminatedInputs(host.cpu.getLevel(), craftingJobId);
        }
    }

    void initializeJobAttachments() {
        var job = host.getJob();
        if (job != null) {
            host.jobAttachmentsForLifecycle()
                .initialize(host.createJobContext(job));
        }
    }

    void clearJobAttachments(ECOCraftingJobResult result) {
        host.jobAttachmentsForLifecycle()
            .clear(result);
    }
}
