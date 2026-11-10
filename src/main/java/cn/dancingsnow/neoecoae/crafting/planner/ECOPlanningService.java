package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.concurrent.Future;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CalculationStrategy;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/**
 * ECO-owned planning boundary. It deliberately does not call
 * {@code ICraftingService.beginCraftingCalculation}, keeping this request outside other planner wrappers.
 */
public final class ECOPlanningService {

    private static final ECOCraftingPlannerService PLANNER = new ECOCraftingPlannerService();

    private ECOPlanningService() {}

    public static Future<ICraftingPlan> begin(World level, GridView grid, IActionSource source, AEKey goal, long amount,
        CalculationStrategy strategy, ECOPlannerOptions options) {
        var inventory = ECOPlannerInventory.capture(grid);
        ECOCraftingPlannerService.Session session = PLANNER.createSession(
            grid.captureCraftingService(level),
            goal,
            inventory,
            options.cyclePlanningEnabled(),
            options.ignorePatternSubstitutions(),
            options.fuzzyPlanningItemIds());
        return begin(session, amount, strategy);
    }

    static Future<ICraftingPlan> begin(ECOCraftingPlannerService.Session session, long amount,
        CalculationStrategy strategy) {
        return ECOPlanningExecutor.submit(() -> {
            ECOPlanningResult result = calculate(session, amount, strategy, ECOPlanningService::checkpoint);
            startImprovements(session, result);
            return result.plan();
        });
    }

    /** Attach only the frozen recipe context for CPU remaining-work searches; confirmation work is complete. */
    public static @Nullable ECOPlanningImprovements startImprovements(ECOCraftingPlannerService.Session session,
        ECOPlanningResult first) {
        if (!isExecutableSuccess(first) || !session.isStructureCurrent()) return null;
        if (first.improvements() != null) return first.improvements();
        var optional = new ECOPlanningImprovements(session, first);
        first.setImprovements(optional);
        // Retain the frozen recipe context for CPU remaining-work searches. The confirmation is final.
        return optional;
    }

    static ICraftingPlan plan(ECOCraftingPlannerService.Session session, AEKey goal, long requestedAmount,
        CalculationStrategy strategy) throws InterruptedException {
        return calculate(session, requestedAmount, strategy, ECOPlanningService::checkpoint).plan();
    }

    private static ECOPlanningResult calculate(ECOCraftingPlannerService.Session session, long requestedAmount,
        CalculationStrategy strategy, ECOCancellation cancellation) throws InterruptedException {
        if (requestedAmount <= 0L) {
            return session.plan(requestedAmount, true, cancellation);
        }

        ECOPlanningResult exact = session.plan(requestedAmount, false, cancellation);
        if (isExecutableSuccess(exact)) {
            return exact;
        }

        if (strategy == CalculationStrategy.CRAFT_LESS) {
            long bestAmount = 0L;
            ECOPlanningResult bestPlan = null;
            for (long step = Long.highestOneBit(requestedAmount); step > 0L; step >>= 1) {
                cancellation.checkpoint();
                long candidate = bestAmount + step;
                if (candidate >= requestedAmount) {
                    continue;
                }
                ECOPlanningResult probe = session.plan(candidate, false, cancellation);
                if (isExecutableSuccess(probe)) {
                    bestAmount = candidate;
                    bestPlan = probe;
                }
            }
            if (bestPlan != null) {
                return bestPlan;
            }
        }

        // The exact calculation already builds the simulation report, including its material and cycle
        // diagnostics. Reuse it after any CRAFT_LESS probes rather than walking the original demand again.
        if (exact.plan() != null) {
            return exact;
        }

        return session.plan(requestedAmount, true, cancellation);
    }

    private static boolean isExecutableSuccess(ECOPlanningResult result) {
        return ECOPlanningImprovements.executable(result);
    }

    private static void checkpoint() throws InterruptedException {
        if (Thread.currentThread()
            .isInterrupted()) {
            throw new InterruptedException("ECO planning request cancelled");
        }
    }
}
