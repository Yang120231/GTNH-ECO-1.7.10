package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;

/**
 * Optional batch dispatch boundary. It owns FastPath preparation, energy reservation, physical push, and rollback;
 * the task scheduler only observes an accepted immutable result.
 */
final class ECOCraftingFastPathDispatcher {

    private final Object dynamicOutputOwner;
    private final ECOCraftingEnergyTransaction energyTransaction;
    private final ECOCraftingDispatchAccounting accounting;

    ECOCraftingFastPathDispatcher(Object dynamicOutputOwner, ECOCraftingEnergyTransaction energyTransaction,
        ECOCraftingDispatchAccounting accounting) {
        this.dynamicOutputOwner = dynamicOutputOwner;
        this.energyTransaction = energyTransaction;
        this.accounting = accounting;
    }

    boolean supportsBatch(ICraftingProvider provider) {
        return ECOFastPathFacade.supports(provider);
    }

    @Nullable
    java.math.BigInteger tryExactDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        double singlePower, IEnergyGrid energyService, Consumer<ICraftingProvider> recordAttempt) {
        return tryExactDispatch(request, provider, singlePower, energyService, recordAttempt, null);
    }

    /** {@code dispatchLimit} caps one exact dispatch when smart doubling is off, as in 1.21. */
    @Nullable
    java.math.BigInteger tryExactDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        double singlePower, IEnergyGrid energyService, Consumer<ICraftingProvider> recordAttempt,
        @Nullable Long dispatchLimit) {
        if (!request.job().exactOrder
            || !(request.inventory() instanceof cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory inventory)) {
            return null;
        }
        var target = ECOFastPathFacade.resolveProvider(provider);
        if (target == null) {
            return null;
        }
        var initialRequest = request.job().tasks.get(request.pattern())
            .remainingExact();
        var requested = dispatchLimit == null ? initialRequest
            : initialRequest.min(java.math.BigInteger.valueOf(dispatchLimit));
        var runtime = request.job().executionRuntime;
        if (runtime != null) requested = runtime.exactAllowance(request.candidate(), requested);
        if (!Double.isFinite(singlePower) || singlePower < 0) return null;
        if (singlePower > 0) {
            requested = cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingHelper.maxAffordableCrafts(
                singlePower,
                requested,
                offer -> energyService.extractAEPower(
                    offer,
                    appeng.api.config.Actionable.SIMULATE,
                    appeng.api.config.PowerMultiplier.CONFIG));
            if (requested.signum() <= 0) {
                return null;
            }
        }
        var context = cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext.create(
            request.pattern(),
            request.inputs(),
            request.outputs(),
            request.remainders(),
            request.level(),
            request.job().link.getCraftingID());
        var batch = cn.dancingsnow.neoecoae.crafting.execution.batch.ECOExactBatchPlanner.prepare(
            target,
            context,
            inventory,
            requested,
            runtime == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf()
                : runtime.protectedStartupSeed(request.candidate()));
        if (batch == null) {
            return null;
        }

        var energy = energyTransaction.reserve(energyService, singlePower, batch.craftCount());
        if (energy == null) {
            return null;
        }
        recordAttempt.accept(provider);
        try {
            if (!batch.submit(energy)) {
                return null;
            }
        } catch (ECOIndeterminateBatchException failure) {
            accounting.failPermanently(request, "INDETERMINATE_EXACT_BATCH_ACCEPTANCE");
            throw failure;
        }
        try {
            accounting.applyExact(request, batch, () -> {}, provider);
        } catch (RuntimeException failure) {
            accounting.failPermanently(request, "POST_ACCEPT_EXACT_BATCH_ACCOUNTING_FAILURE");
            throw failure;
        }
        return batch.craftCount();
    }

    /**
     * Attempts only the optional batch path. {@code null} means ordinary one-copy fallback remains available.
     */
    @Nullable
    ECOCraftingDispatchResult tryDispatch(ECOCraftingDispatchRequest request, ICraftingProvider provider,
        double singlePower, IEnergyGrid energyService, Consumer<ICraftingProvider> recordProviderAttempt) {
        ECOFastPathFacade.PreparedBatch batch = ECOFastPathFacade.prepare(
            provider,
            request.pattern(),
            request.inputs(),
            request.outputs(),
            request.remainders(),
            request.inventory(),
            safeWaitingBatch(request, singlePower),
            singlePower,
            energyService,
            request.level(),
            request.job().link.getCraftingID(),
            request.job().exactOrder);
        if (batch != null && request.job().executionRuntime != null
            && !(request.job().exactOrder
                ? request.job().executionRuntime
                    .preservesStartupSeeds(request.candidate(), batch.exactInputTotal(), request.inventory())
                : request.job().executionRuntime
                    .preservesStartupSeeds(request.candidate(), batch.inputTotal(), request.inventory()))) {
            // The batch calculator sees the physical CPU inventory. Do not cross another phase's seed lease;
            // ordinary fallback still uses the protected input preview selected by the scheduler.
            batch = null;
        }
        if (batch == null) return null;

        long batchCount = batch.craftCount();
        boolean accepted = false;
        recordProviderAttempt.accept(provider);
        try {
            accepted = batch.submit(ignored -> energyTransaction.reserve(energyService, singlePower, batchCount));
        } catch (ECOIndeterminateBatchException failure) {
            accounting.failPermanently(request, "INDETERMINATE_FAST_PATH_ACCEPTANCE");
            throw failure;
        } catch (RuntimeException ignored) {}
        if (!accepted) {
            return null;
        }

        ECOCraftingDispatchResult result = ECOCraftingDispatchResult
            .batch(batch.craftCount(), batch.outputs(), batch.remainders());
        try {
            accounting.apply(request, result, () -> {}, provider);
        } catch (RuntimeException failure) {
            accounting.failPermanently(request, "POST_ACCEPT_FAST_PATH_ACCOUNTING_FAILURE");
            throw failure;
        }
        return result;
    }

    static long safeWaitingBatch(ECOCraftingDispatchRequest request, double singlePower) {
        long limit = Math.min(request.allowedCrafts(), ECOCraftingEnergyTransaction.maxSafeCrafts(singlePower));
        if (request.job().exactOrder) return limit;
        var perCraft = new java.util.HashMap<cn.dancingsnow.neoecoae.platform.ae2.key.AEKey, Long>();
        try {
            for (var entry : request.outputs()) perCraft.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            for (var entry : request.remainders()) perCraft.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            for (var entry : perCraft.entrySet()) {
                if (entry.getValue() <= 0L) return 0L;
                long waiting = request.job().waitingFor.list.get(entry.getKey());
                if (waiting < 0L) return 0L;
                limit = Math.min(limit, (Long.MAX_VALUE - waiting) / entry.getValue());
            }
            return limit;
        } catch (ArithmeticException overflow) {
            return 0L;
        }
    }
}
