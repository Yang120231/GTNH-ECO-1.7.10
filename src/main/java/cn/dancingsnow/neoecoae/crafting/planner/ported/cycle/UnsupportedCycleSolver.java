package cn.dancingsnow.neoecoae.crafting.planner.ported.cycle;

import cn.dancingsnow.neoecoae.crafting.planner.ported.ECOCancellation;

/** Deliberately inert solver, kept so the extension point can always be disabled without code changes. */
public final class UnsupportedCycleSolver implements CycleSolver {

    @Override
    public CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        return CycleSolveResult.notImplemented("Cycle solver is intentionally disabled in this configuration");
    }
}
