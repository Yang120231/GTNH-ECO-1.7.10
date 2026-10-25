package cn.dancingsnow.neoecoae.api.me.planning;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;

/**
 * ECO settings and diagnostics associated with one AE2 crafting calculation.
 */
public interface ECOCraftingCalculationSettings {

    boolean neoecoae$isIgnoringPatternSubstitutions();

    /** Latest structured ECO result, retained for diagnostics and the future ECO Crafting Plan UI. */
    @Nullable
    ECOPlanningResult neoecoae$getLastPlanningResult();
}
