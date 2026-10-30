package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

public final class CraftingPlan implements ICraftingPlan, ECOCraftingPlanDiagnostics {

    private final GenericStack output;
    private final long bytes;
    private final boolean simulation, multiplePaths;
    private final KeyCounter used, emitted, missing;
    private final Map<IPatternDetails, Long> tasks;
    private volatile ECOPlanningResult diagnostics;
    /** Planned by AE2's own planner: an ECO CPU must then execute it exactly like a native crafting CPU. */
    private boolean vanillaExecution;

    public CraftingPlan(GenericStack output, long bytes, boolean simulation, boolean multiplePaths, KeyCounter used,
        KeyCounter emitted, KeyCounter missing, Map<IPatternDetails, Long> tasks) {
        this.output = Objects.requireNonNull(output);
        this.bytes = bytes;
        this.simulation = simulation;
        this.multiplePaths = multiplePaths;
        this.used = used.copy();
        this.emitted = emitted.copy();
        this.missing = missing.copy();
        this.tasks = Collections.unmodifiableMap(new LinkedHashMap<>(tasks));
    }

    public CraftingPlan markVanillaExecution() {
        vanillaExecution = true;
        return this;
    }

    public boolean vanillaExecution() {
        return vanillaExecution;
    }

    @Override
    public GenericStack finalOutput() {
        return output;
    }

    @Override
    public long bytes() {
        return bytes;
    }

    @Override
    public boolean simulation() {
        return simulation;
    }

    @Override
    public boolean multiplePaths() {
        return multiplePaths;
    }

    @Override
    public KeyCounter usedItems() {
        return used;
    }

    @Override
    public KeyCounter emittedItems() {
        return emitted;
    }

    @Override
    public KeyCounter missingItems() {
        return missing;
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return tasks;
    }

    @Override
    public ECOPlanningResult neoecoae$getPlanningResult() {
        return diagnostics;
    }

    @Override
    public void neoecoae$setPlanningResult(ECOPlanningResult result) {
        diagnostics = result;
    }
}
