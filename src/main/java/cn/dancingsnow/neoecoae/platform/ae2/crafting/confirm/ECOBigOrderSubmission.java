package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingNativeRuntime;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingSubmitResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpuSelection;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;

/**
 * Server half of 1.21's big-order start ({@code CraftConfirmMenuMixin.neoecoae$startBigOrder}): the confirmed ECO
 * result is handed to an idle ECO CPU as one exact order, or, for a forced shortage with a cycle, as a parent order
 * that plans executable segments once the materials arrive. AE2's long-only submission is never involved.
 */
final class ECOBigOrderSubmission {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");

    private ECOBigOrderSubmission() {}

    /** Why a big order was not accepted: the error page entry and the server-log reason. */
    static final class Rejection {

        final ECOCraftConfirmError error;
        final String reason;
        final String detail;

        Rejection(ECOCraftConfirmError error, String reason, String detail) {
            this.error = error;
            this.reason = reason;
            this.detail = detail;
        }
    }

    /** The idle ECO CPU a big order would go to, or {@code null} (1.21's resolveBigOrderCpu). */
    static @Nullable NativeECOCpu resolveCpu(@Nullable IGrid grid, @Nullable ICraftingCPU selected,
        BaseActionSource source) {
        if (grid == null) return null;
        ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        if (crafting == null) return null;
        List<ICraftingCPU> published = new ArrayList<>();
        for (ICraftingCPU cpu : crafting.getCpus()) published.add(cpu);
        ICraftingCPU picked = ECOCraftConfirmRules.pickBigOrderCpu(
            selected,
            published,
            ECOBigOrderSubmission::idleEco,
            cpu -> NativeECOCpuSelection.allows((CraftingCPUCluster) cpu, source));
        return picked instanceof NativeECOCpu ? (NativeECOCpu) picked : null;
    }

    private static boolean idleEco(ICraftingCPU cpu) {
        if (!(cpu instanceof NativeECOCpu eco)) return false;
        return eco.isActive() && !eco.isBusy()
            && eco.runtime()
                .hasFreeThread()
            && eco.runtime() instanceof ECOCraftingNativeRuntime;
    }

    /**
     * Admission, CPU resolution, inventory check and submission of the confirmed result.
     *
     * @return {@code null} once an ECO CPU accepted the order
     */
    static @Nullable Rejection submit(ECOPlanningResult exact, @Nullable ECOPlannerOptions options, boolean forced,
        @Nullable IGrid grid, @Nullable ICraftingCPU selected, BaseActionSource source) {
        if (options == null) return new Rejection(ECOCraftConfirmError.INCOMPLETE_PLAN, "NO_PLANNER_OPTIONS", "");
        boolean waitingForMaterials = ECOBigOrderAdmission.waitsForMaterials(exact, forced);
        if (!waitingForMaterials && !ECOBigOrderAdmission.allows(exact, forced)) {
            LOGGER.warn(
                "[big-order-submit] Admission denied: status={}, forced={}, hasPlan={}",
                exact.status(),
                forced,
                exact.plan() != null);
            return new Rejection(ECOCraftConfirmError.INCOMPLETE_PLAN, "ADMISSION_DENIED", "");
        }
        if (grid == null) return new Rejection(ECOCraftConfirmError.CPU_OFFLINE, "NO_GRID", "");
        NativeECOCpu cpu = resolveCpu(grid, selected, source);
        if (cpu == null) return new Rejection(ECOCraftConfirmError.BIG_ORDER_NO_CPU, "NO_ELIGIBLE_ECO_CPU", "");
        GridView view = GridView.of(grid);
        IActionSource action = IActionSource.of(source);
        if (!forced) {
            var inventory = ECOPlannerInventory.capture(view);
            var storage = view.getStorageService()
                .getInventory();
            for (var node : exact.trace()
                .nodes()) {
                if (node.key() == null || node.exactFromInventory()
                    .signum() <= 0) continue;
                BigInteger required = node.exactFromInventory();
                // Validate the full order; the exact ledger owns deferred inputs beyond long.
                if (!ECOBigOrderAdmission.hasStoredAmount(
                    required,
                    inventory.isUnbounded(node.key()),
                    amount -> storage.extract(node.key(), amount, Actionable.SIMULATE, action))) {
                    LOGGER.warn("[big-order-submit] Inventory check failed: key={}, required={}", node.key(), required);
                    return new Rejection(
                        ECOCraftConfirmError.MISSING_INGREDIENT,
                        "MISSING_INGREDIENT",
                        node.key()
                            .getDisplayName() + " x"
                            + required);
                }
            }
        }
        ECOCraftingCPU target = ((ECOCraftingNativeRuntime) cpu.runtime()).cpu();
        final ICraftingSubmitResult submitted;
        try {
            LOGGER.info(
                "[big-order-submit] Submitting to CPU: output={}, forced={}, waitingForMaterials={}, cpu={}",
                exact.plan() == null ? null
                    : exact.plan()
                        .finalOutput(),
                forced,
                waitingForMaterials,
                cpu.getName());
            if (waitingForMaterials) {
                // A missing cycle has no executable schedule. Publish a persistent parent and let its planner
                // create complete children after stock (including startup seeds) arrives.
                var request = ECOBigOrderRequest.fromPlanningResult(exact, true, options);
                submitted = request.submit(
                    carrier -> target.getCluster()
                        .submitJob(view, carrier, action, null));
            } else {
                submitted = target.getCluster()
                    .submitJob(view, new ECOExactCraftingPlan(exact, forced), action, null);
            }
        } catch (RuntimeException invalid) {
            LOGGER.warn("[big-order-submit] Exact order rejected", invalid);
            return new Rejection(ECOCraftConfirmError.INCOMPLETE_PLAN, "EXACT_ORDER_INVALID", String.valueOf(invalid));
        }
        if (submitted.successful()) return null;
        String detail = submitted.missingIngredient() == null ? ""
            : submitted.missingIngredient()
                .what()
                .getDisplayName() + " x"
                + submitted.missingIngredient()
                    .amount();
        return new Rejection(
            ECOCraftConfirmRules.submitError(submitted.errorCode()),
            "CPU_SUBMISSION_FAILED:" + submitted.errorCode(),
            detail);
    }
}
