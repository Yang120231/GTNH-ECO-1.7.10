package cn.dancingsnow.neoecoae.crafting.runtime;

/** Implemented on GTNH's base CPU, so normal and extended clusters share the same contract. */
public interface ECOExecutionHost {

    ECOExecutionRuntime neoecoae$getExecution();

    void neoecoae$setExecution(ECOExecutionRuntime execution);

    void neoecoae$installExecution(cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionPlan plan);
}
