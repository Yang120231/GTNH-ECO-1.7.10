package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/**
 * The ECO report of a plan AE2's own planner made, for confirmations shown on the ECO page because the fast planner
 * is enabled although the request was planned natively. The rows carry exactly what GTNH's confirmation sends to its
 * own page ({@code ContainerCraftConfirm.detectAndSendChanges}): extracted, missing, crafted, executions and the usage
 * share of the stock at planning time.
 */
public final class NativeCraftConfirmReports {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");

    private NativeCraftConfirmReports() {}

    public static ECOCraftConfirmReports.Built build(ICraftingJob job, BaseActionSource source,
        boolean cyclePlanningEnabled, long version) {
        boolean simulation = job.isSimulation();
        List<ECOCraftConfirmReport.Row> rows = rows(job, source);
        ECOCraftConfirmState state = new ECOCraftConfirmState(
            true,
            true,
            cyclePlanningEnabled,
            false,
            0L,
            BigInteger.ZERO,
            // AE2 cannot run an incomplete native plan; UNSUPPORTED keeps the force start closed for it.
            simulation ? PlanningStatus.UNSUPPORTED : null,
            "",
            0,
            simulation,
            job.getByteTotal(),
            version,
            null,
            "");
        return new ECOCraftConfirmReports.Built(
            state,
            new ECOCraftConfirmReport(version, rows, ECOCycleItemList.EMPTY));
    }

    /** One row per planned stack, with the amounts GTNH's own page shows. */
    static List<ECOCraftConfirmReport.Row> rows(ICraftingJob job, BaseActionSource source) {
        boolean simulation = job.isSimulation();
        List<ECOCraftConfirmReport.Row> rows = new ArrayList<>();
        try {
            IItemList<IAEStack<?>> plan = AEApi.instance()
                .storage()
                .createAEStackList();
            job.populatePlan(plan);
            MECraftingInventory storage = job.getStorageAtBeginning();
            for (IAEStack<?> planned : plan) {
                AEKey key = AEKey.fromNative(planned);
                if (key == null) continue;
                long extract = planned.getStackSize();
                long missing = 0;
                if (simulation && storage != null) {
                    long extractable = extractable(storage, planned, extract, source);
                    missing = extract - extractable;
                    extract = extractable;
                }
                long craft = planned.getCountRequestable();
                long available = 0;
                if (extract > 0 && craft <= 0 && missing <= 0 && storage != null)
                    available = extractable(storage, planned, Long.MAX_VALUE, source);
                rows.add(
                    new ECOCraftConfirmReport.Row(
                        key,
                        BigInteger.valueOf(Math.max(0, extract)),
                        BigInteger.valueOf(Math.max(0, missing)),
                        BigInteger.valueOf(Math.max(0, craft)),
                        BigInteger.ZERO,
                        BigInteger.valueOf(Math.max(0, planned.getCountRequestableCrafts())),
                        BigInteger.valueOf(Math.max(0, available))));
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("[craft-confirm] Native plan report failed", failure);
        }
        return rows;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static long extractable(MECraftingInventory storage, IAEStack<?> planned, long amount,
        BaseActionSource source) {
        IAEStack request = planned.copy();
        request.reset();
        request.setStackSize(amount);
        IAEStack extracted = storage.extractItems(request, Actionable.SIMULATE, source);
        return extracted == null ? 0 : extracted.getStackSize();
    }
}
