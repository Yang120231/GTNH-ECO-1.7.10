package cn.dancingsnow.neoecoae.platform.forge;

import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningExecutor;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;

/** The planning lifetime belongs to a server, not to the client JVM. */
public final class PlanningLifecycle {

    private PlanningLifecycle() {}

    public static void started() {
        PatternRuntime.onRecipeReloadOrServerReload();
        ECOPlanningExecutor.started();
    }

    public static void stopped() {
        PatternRuntime.onRecipeReloadOrServerReload();
        ECOPlanningExecutor.stopped();
    }
}
