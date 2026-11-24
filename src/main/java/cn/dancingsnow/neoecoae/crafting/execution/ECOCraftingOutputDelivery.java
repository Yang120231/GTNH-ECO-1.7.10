package cn.dancingsnow.neoecoae.crafting.execution;

import java.math.BigInteger;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimRequest;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputClaimResult;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferQuarantine;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.TemporaryCraftingOrder;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

/** Owns CPU output claims, delivery retries and the dynamic final-output buffer. */
final class ECOCraftingOutputDelivery {

    private final ECOCraftingCPULogic host;
    private final Object2LongLinkedOpenHashMap<AEKey> pendingFinalOutputs = new Object2LongLinkedOpenHashMap<>();
    private final KeyCounter blockedFinalOutputs = new KeyCounter();
    private boolean deliveringFinalOutput;
    private boolean receivingExactOutput;
    private boolean uncertainFinalDelivery;
    private @Nullable ExecutingCraftingJob blockedDeliveryJob;

    ECOCraftingOutputDelivery(ECOCraftingCPULogic host) {
        this.host = host;
    }

    void clearPendingFinalOutputs() {
        pendingFinalOutputs.clear();
        uncertainFinalDelivery = false;
        clearBlockedDelivery();
    }

    boolean hasUncertainFinalDelivery() {
        return uncertainFinalDelivery;
    }

    boolean isDeliveringFinalOutput() {
        return deliveringFinalOutput;
    }

    boolean isFinalOutputDeliveryBlocked() {
        var current = host.getJob();
        return current != null
            && (uncertainFinalDelivery || current == blockedDeliveryJob && current.remainingAmount > 0L
                && (current.waitingFor.list.isEmpty()
                    || current.finalOutput != null && TemporaryCraftingOrder.isOrder(current.finalOutput.what()))
                && !host.taskSchedulerForOutput()
                    .hasPendingTasks(current));
    }

    void collectBlockedFinalOutputs(KeyCounter output) {
        if (!isFinalOutputDeliveryBlocked()) return;
        var inventory = host.getInventory().list;
        for (var entry : blockedFinalOutputs) {
            AEKey key = entry.getKey();
            long amount = Math.min(entry.getLongValue(), inventory.get(key));
            if (amount > 0) output.set(key, amount);
        }
    }

    private void clearBlockedDelivery() {
        blockedDeliveryJob = null;
        blockedFinalOutputs.clear();
    }

    boolean hasPendingFinalOutputs() {
        return !pendingFinalOutputs.isEmpty();
    }

    boolean isRequestedWorkDelivered(ExecutingCraftingJob current) {
        return !uncertainFinalDelivery && current.remainingAmount <= 0L
            && !host.taskSchedulerForOutput()
                .hasPendingTasks(current);
    }

    void deliverStoredFinalOutput() {
        clearBlockedDelivery();
        var current = host.getJob();
        if (current == null || uncertainFinalDelivery || deliveringFinalOutput) {
            return;
        }
        if (current.finalOutput != null && TemporaryCraftingOrder.isOrder(current.finalOutput.what())) {
            deliverTemporaryOrder(current);
            return;
        }
        deliverPendingFinalOutputs(current);
        if (host.getJob() != current || current.finalOutput == null || uncertainFinalDelivery) {
            return;
        }
        AEKey key = current.finalOutput.what();
        long storedFinalOutput = host.getInventory().list.get(key);
        if (storedFinalOutput > 0L) {
            PlannerAmount reserve = PlannerAmount.ZERO;
            for (var task : current.tasks.entrySet()) {
                reserve = reserve.add(
                    ECOPhaseScheduler.growingPatternFeedbackReserveExact(task.getKey(), task.getValue().value, key));
            }
            if (current.executionRuntime != null) {
                reserve = reserve.max(PlannerAmount.of(current.executionRuntime.reservedInputAmount(key)));
            }
            // Keep material reserved for the next growth phase, then deliver only the excess final output.
            PlannerAmount deliverable = PlannerAmount.of(storedFinalOutput)
                .subtract(reserve)
                .max(PlannerAmount.ZERO);
            long amount = deliverable.min(PlannerAmount.of(Math.max(0L, current.remainingAmount)))
                .longValueExact();
            if (amount > 0L) {
                long inserted = deliverFinalOutputToDestination(current, key, amount);
                if (inserted > 0L) current.remainingAmount = Math.max(0L, current.remainingAmount - inserted);
                host.settleTransferredInventory(
                    key,
                    BigInteger.valueOf(ECOTransferQuarantine.unavailable(inserted, amount)));
            }
        }
        // Like GTNH's completeJob, finish after all tasks and the requested output are delivered. Optional
        // byproducts (including chanced outputs) need not return, and surplus dynamic outputs stay in inventory.
        if (isRequestedWorkDelivered(current)) {
            host.finishJob(true);
        }
    }

    /** Keeps delivery progress in the existing persisted buffer; cancellation returns the same physical inventory. */
    private void deliverTemporaryOrder(ExecutingCraftingJob current) {
        TemporaryCraftingOrder.Pattern order = null;
        for (var task : current.tasks.entrySet()) {
            if (task.getKey() instanceof TemporaryCraftingOrder.Pattern candidate && candidate.getDefinition()
                .equals(current.finalOutput.what())) order = candidate;
            else if (task.getValue().value > 0L) return;
        }
        if (order == null) return;
        var progress = current.tasks.get(order);
        if (progress.value > 0L) {
            if (progress.value != 1L) throw new IllegalStateException("A temporary order must be collected once");
            for (GenericStack stack : order.collected())
                if (host.getInventory().list.get(stack.what()) < stack.amount()) return;
            if (current.executionRuntime == null) progress.accept(1L);
            else {
                ECOExecutionRuntime.DispatchCandidate ready = null;
                for (var candidate : current.executionRuntime.candidates())
                    if (candidate.pattern() == order && candidate.maxDispatchCount() > 0L) {
                        ready = candidate;
                        break;
                    }
                if (ready == null) return;
                current.executionRuntime.onVirtualAccepted(ready, 1L);
            }
            for (GenericStack stack : order.collected()) pendingFinalOutputs.put(stack.what(), stack.amount());
            host.markCpuDirty();
        }
        deliverPendingFinalOutputs(current);
        if (host.getJob() != current || uncertainFinalDelivery || !pendingFinalOutputs.isEmpty()) return;
        current.remainingAmount = 0L;
        current.timeTracker.decrementItems(
            1L,
            current.finalOutput.what()
                .getType());
        host.postChange(current.finalOutput.what());
        host.finishJob(true);
    }

    long insert(AEKey what, long amount, Actionable type) {
        return insert(what, amount, type, null);
    }

    long insert(AEKey what, long amount, Actionable type, @Nullable Long preflightAccepted) {
        return insert(what, amount, type, preflightAccepted, null);
    }

    long insert(AEKey what, long amount, Actionable type, @Nullable Long preflightAccepted,
        @Nullable Boolean preflightMatchesFinalOutput) {
        var current = host.getJob();
        if (what == null || amount <= 0L || current == null) {
            return 0L;
        }
        if (ECOCraftingJobLifecycle.isTerminated(host.cpu.getLevel(), current.link.getCraftingID())) {
            return 0L;
        }
        boolean matchesFinalOutput = preflightMatchesFinalOutput != null ? preflightMatchesFinalOutput
            : current.finalOutput != null && what.matches(current.finalOutput);
        if (deliveringFinalOutput && (matchesFinalOutput
            || current.finalOutput != null && TemporaryCraftingOrder.isOrder(current.finalOutput.what()))) {
            return 0L;
        }
        long accepted = preflightAccepted != null ? preflightAccepted
            : current.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (accepted <= 0L) {
            return 0L;
        }
        if (type == Actionable.MODULATE) {
            host.getInventory()
                .insert(what, accepted, Actionable.MODULATE);
            host.taskSchedulerForOutput()
                .recordPhysicalInsert(what);
            long claimed = current.waitingFor.extract(what, accepted, Actionable.MODULATE);
            if (host.getJob() == current && claimed > 0L)
                current.producedTotals.merge(what, java.math.BigInteger.valueOf(claimed), java.math.BigInteger::add);
            host.recordCompletedCraftingWork(accepted, what.getType());
            host.markCpuDirty();
        }
        return accepted;
    }

    long insertForJob(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        var current = host.getJob();
        if (what == null || amount <= 0L
            || craftingJobId == null
            || current == null
            || !craftingJobId.equals(current.link.getCraftingID())
            || ECOCraftingJobLifecycle.isTerminated(host.cpu.getLevel(), craftingJobId)) {
            return 0L;
        }
        long accepted = host.insert(what, amount, type);
        if (accepted < 0L || accepted > amount) {
            throw new IllegalStateException("Invalid CPU insertion amount: " + accepted + " for " + amount);
        }
        // Directed output belongs to this CPU, but must not consume unrelated waiting entries.
        if (type == Actionable.MODULATE && accepted < amount) {
            host.getInventory()
                .insert(what, amount - accepted, Actionable.MODULATE);
            host.taskSchedulerForOutput()
                .recordPhysicalInsert(what);
            host.markCpuDirty();
        }
        return amount;
    }

    java.math.BigInteger insertExactForJob(UUID jobId, AEKey key, java.math.BigInteger amount, Actionable mode) {
        var current = host.getJob();
        if (receivingExactOutput || jobId == null
            || key == null
            || amount.signum() <= 0
            || current == null
            || !jobId.equals(current.link.getCraftingID())
            || ECOCraftingJobLifecycle.isTerminated(host.cpu.getLevel(), jobId)) return java.math.BigInteger.ZERO;
        var max = java.math.BigInteger.valueOf(Long.MAX_VALUE);
        if (amount.bitLength() <= 63 || !current.exactOrder
            || !(current.waitingFor instanceof cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory waiting)) {
            return java.math.BigInteger.valueOf(
                insertForJob(
                    jobId,
                    key,
                    amount.min(max)
                        .longValueExact(),
                    mode));
        }
        if (mode == Actionable.SIMULATE) return amount;
        receivingExactOutput = true;
        try {
            var claimed = waiting.amount(key)
                .min(amount);
            if (claimed.signum() > 0
                && !waiting.debit(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, claimed)))
                return java.math.BigInteger.ZERO;
            // ExactInventory commits all keys before notifying listeners. Even if a listener fails,
            // the physical receipt is retained and must not be replayed by its worker.
            try {
                host.exactInventory()
                    .setEnabled(true);
                host.exactInventory()
                    .restore(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, amount));
            } catch (RuntimeException ignored) {}
            if (host.getJob() == current) {
                current.producedTotals.merge(key, claimed, java.math.BigInteger::add);
                host.recordCompletedCraftingWork(
                    claimed.min(max)
                        .longValueExact(),
                    key.getType());
                host.taskSchedulerForOutput()
                    .recordPhysicalInsert(key);
            }
            host.markCpuDirty();
            return amount;
        } finally {
            receivingExactOutput = false;
        }
    }

    ECOCraftingOutputClaimResult claimCraftingOutput(ECOCraftingOutputClaimRequest request) {
        long requested = request == null ? 0L : Math.max(0L, request.amount());
        if (request == null || request.craftingJobId() == null
            || request.expectedKey() == null
            || request.actualKey() == null
            || request.amount() <= 0L
            || request.mode() == null) {
            return claimResult(ECOCraftingOutputClaimResult.Status.INVALID_REQUEST, requested, 0L, 0L);
        }

        var current = host.getJob();
        if (current == null) {
            return claimResult(ECOCraftingOutputClaimResult.Status.NO_JOB, requested, 0L, 0L);
        }
        if (!request.craftingJobId()
            .equals(current.link.getCraftingID())
            || ECOCraftingJobLifecycle.isTerminated(host.cpu.getLevel(), request.craftingJobId())) {
            return claimResult(
                ECOCraftingOutputClaimResult.Status.TERMINAL,
                requested,
                0L,
                Math.max(0L, current.remainingAmount));
        }

        long claim = current.waitingFor.extract(request.expectedKey(), request.amount(), Actionable.SIMULATE);
        if (claim <= 0L) {
            return claimResult(
                ECOCraftingOutputClaimResult.Status.NO_MATCH,
                requested,
                0L,
                Math.max(0L, current.remainingAmount));
        }

        boolean finalOutputClaim = current.finalOutput != null && request.expectedKey()
            .equals(current.finalOutput.what());
        if (finalOutputClaim && (uncertainFinalDelivery || deliveringFinalOutput)) return claimResult(
            ECOCraftingOutputClaimResult.Status.NO_MATCH,
            requested,
            0L,
            Math.max(0L, current.remainingAmount));
        if (request.mode() == Actionable.SIMULATE) {
            OutputRoute route = routeClaimedOutput(
                current,
                request.actualKey(),
                claim,
                request.mode(),
                finalOutputClaim);
            long remaining = finalOutputClaim ? Math.max(0L, current.remainingAmount - route.deliveredAmount())
                : Math.max(0L, current.remainingAmount);
            return new ECOCraftingOutputClaimResult(
                ECOCraftingOutputClaimResult.Status.ACCEPTED,
                requested,
                claim,
                route.deliveredToRequester(),
                route.deliveredToNetwork(),
                route.storedInCpu(),
                remaining,
                false);
        }

        long claimed = current.waitingFor.extract(request.expectedKey(), claim, Actionable.MODULATE);
        if (claimed <= 0L) {
            return claimResult(
                ECOCraftingOutputClaimResult.Status.NO_MATCH,
                requested,
                0L,
                Math.max(0L, current.remainingAmount));
        }
        // The waiting inventory is server-thread local. A mismatch can only be caused by a re-entrant callback;
        // route no more than the amount actually removed and retain the uncommitted tail in the CPU.
        claim = Math.min(claim, claimed);
        OutputRoute route = routeClaimedOutput(
            current,
            request.actualKey(),
            claim,
            Actionable.MODULATE,
            finalOutputClaim);

        if (route == null) {
            host.markCpuDirty();
            return claimResult(
                ECOCraftingOutputClaimResult.Status.INDETERMINATE,
                requested,
                claim,
                Math.max(0L, current.remainingAmount));
        }

        if (route.storedInCpu() > 0L) {
            host.getInventory()
                .insert(request.actualKey(), route.storedInCpu(), Actionable.MODULATE);
            host.taskSchedulerForOutput()
                .recordPhysicalInsert(request.actualKey());
            if (finalOutputClaim && current.finalOutput != null
                && !request.actualKey()
                    .equals(current.finalOutput.what())) {
                pendingFinalOutputs.mergeLong(request.actualKey(), route.storedInCpu(), NEMath::saturatingAdd);
            }
        }
        if (host.getJob() == current) current.producedTotals
            .merge(request.expectedKey(), java.math.BigInteger.valueOf(claim), java.math.BigInteger::add);
        host.recordCompletedCraftingWork(
            claim,
            request.actualKey()
                .getType());
        if (finalOutputClaim) {
            current.remainingAmount = Math.max(0L, current.remainingAmount - route.deliveredAmount());
        }
        if (route.deliveredToRequester() > 0L || route.deliveredToNetwork() > 0L) {
            host.postChange(request.actualKey());
        }
        host.markCpuDirty();

        boolean finished = finishIfComplete(current);
        long remaining = finished ? 0L : Math.max(0L, current.remainingAmount);
        return new ECOCraftingOutputClaimResult(
            ECOCraftingOutputClaimResult.Status.ACCEPTED,
            requested,
            claim,
            route.deliveredToRequester(),
            route.deliveredToNetwork(),
            route.storedInCpu(),
            remaining,
            finished);
    }

    private ECOCraftingOutputClaimResult claimResult(ECOCraftingOutputClaimResult.Status status, long requested,
        long claimed, long remaining) {
        return new ECOCraftingOutputClaimResult(status, requested, claimed, 0L, 0L, 0L, remaining, false);
    }

    private @Nullable OutputRoute routeClaimedOutput(ExecutingCraftingJob current, AEKey actualKey, long amount,
        Actionable mode, boolean finalOutputClaim) {
        if (!finalOutputClaim) {
            return new OutputRoute(0L, 0L, amount);
        }

        boolean standalone = current.link.isStandalone();
        long inserted = sendFinalOutput(current, actualKey, amount, mode, standalone);
        if (inserted == ECOTransferQuarantine.UNKNOWN) return null;
        return standalone ? new OutputRoute(0L, inserted, amount - inserted)
            : new OutputRoute(inserted, 0L, amount - inserted);
    }

    /** Retries dynamic final keys that could not be delivered during their original claim. */
    private void deliverPendingFinalOutputs(ExecutingCraftingJob current) {
        if (pendingFinalOutputs.isEmpty()) {
            return;
        }
        boolean order = current.finalOutput != null && TemporaryCraftingOrder.isOrder(current.finalOutput.what());
        // Delivery can re-enter this CPU through the destination, so walk a key snapshot and re-read each amount.
        for (AEKey key : pendingFinalOutputs.keySet()
            .toArray(new AEKey[0])) {
            long stored = Math.min(pendingFinalOutputs.getLong(key), host.getInventory().list.get(key));
            if (stored <= 0L) {
                if (order) blockedDeliveryJob = current;
                else pendingFinalOutputs.removeLong(key);
                continue;
            }
            long amount = order ? stored : Math.min(stored, Math.max(0L, current.remainingAmount));
            if (amount <= 0L) {
                continue;
            }
            long inserted = deliverFinalOutputToDestination(current, key, amount);
            long unavailable = ECOTransferQuarantine.unavailable(inserted, amount);
            if (unavailable > 0L) {
                long remaining = Math.max(0L, pendingFinalOutputs.getLong(key) - unavailable);
                if (remaining == 0L) {
                    pendingFinalOutputs.removeLong(key);
                } else {
                    pendingFinalOutputs.put(key, remaining);
                }
                if (!order && inserted > 0L) current.remainingAmount = Math.max(0L, current.remainingAmount - inserted);
                host.settleTransferredInventory(key, BigInteger.valueOf(unavailable));
            }
            if (inserted == ECOTransferQuarantine.UNKNOWN || host.getJob() != current) return;
        }
    }

    private long deliverFinalOutputToDestination(ExecutingCraftingJob current, AEKey key, long amount) {
        long accepted = sendFinalOutput(current, key, amount, Actionable.MODULATE, current.link.isStandalone());
        if (accepted >= 0L && accepted < amount) {
            blockedDeliveryJob = current;
            blockedFinalOutputs.set(key, amount - accepted);
        }
        return accepted;
    }

    private long sendFinalOutput(ExecutingCraftingJob current, AEKey key, long amount, Actionable mode,
        boolean standalone) {
        java.util.function.LongSupplier send;
        try {
            if (standalone) {
                GridView grid = host.cpu.getGrid();
                if (grid == null) return 0L;
                var storage = grid.getStorageService()
                    .getInventory();
                var source = host.cpu.getActionSource();
                send = () -> storage.insert(key, amount, mode, source);
            } else {
                send = () -> current.link.insert(key, amount, mode);
            }
            if (mode == Actionable.SIMULATE) return ECOTransferQuarantine.check(send.getAsLong(), amount);
        } catch (RuntimeException failure) {
            return 0L;
        }
        deliveringFinalOutput = true;
        try {
            long accepted = host.transfers.attempt(key, amount, send);
            if (accepted == ECOTransferQuarantine.UNKNOWN && host.getJob() == current) uncertainFinalDelivery = true;
            return accepted;
        } finally {
            deliveringFinalOutput = false;
        }
    }

    private boolean finishIfComplete(ExecutingCraftingJob expected) {
        if (host.getJob() != expected) {
            return !host.hasJob();
        }
        if (!isRequestedWorkDelivered(expected)) {
            return false;
        }
        host.finishJob(true);
        return !host.hasJob();
    }

    void loadPendingFinalOutputs(CompoundTag jobData, HolderLookup.Provider registries) {
        uncertainFinalDelivery = jobData.getBoolean("uncertainFinalDelivery");
        ListTag entries = jobData.getList("pendingFinalOutputs", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            try {
                GenericStack stack = GenericStack.readTag(registries, entries.getCompound(index));
                if (stack != null && stack.amount() > 0L) {
                    pendingFinalOutputs.mergeLong(stack.what(), stack.amount(), NEMath::saturatingAdd);
                }
            } catch (RuntimeException ignored) {}
        }
    }

    void writePendingFinalOutputs(CompoundTag jobData, HolderLookup.Provider registries) {
        if (uncertainFinalDelivery) jobData.putBoolean("uncertainFinalDelivery", true);
        if (pendingFinalOutputs.isEmpty()) {
            jobData.remove("pendingFinalOutputs");
            return;
        }
        ListTag entries = new ListTag();
        for (var entry : pendingFinalOutputs.object2LongEntrySet()) {
            if (entry.getLongValue() > 0L) {
                entries.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        if (entries.isEmpty()) {
            jobData.remove("pendingFinalOutputs");
        } else {
            jobData.put("pendingFinalOutputs", entries);
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record OutputRoute(long deliveredToRequester, long deliveredToNetwork, long storedInCpu) {

        long deliveredAmount() {
            return NEMath.saturatingAdd(deliveredToRequester, deliveredToNetwork);
        }
    }
}
