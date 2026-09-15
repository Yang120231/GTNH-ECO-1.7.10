package cn.dancingsnow.neoecoae.crafting.planner.ported.compile;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.ported.solve.PlannerAmount;

/** Boundary for already validated legacy recipe snapshots, including gross returned outputs. */
public final class CompiledPattern {

    private final int id;
    private final Object details;
    private final Object key;
    private final PlannerAmount amount;
    private final List<CompiledInput> inputs;
    private final List<GenericStack> outputs;

    public CompiledPattern(int id, Object details, Object key, long amount, List<CompiledInput> inputs,
        List<GenericStack> outputs) {
        this.id = id;
        this.details = details;
        this.key = key;
        this.amount = PlannerAmount.of(amount);
        this.inputs = com.google.common.collect.ImmutableList.copyOf(inputs);
        this.outputs = com.google.common.collect.ImmutableList.copyOf(outputs);
    }

    public int id() {
        return id;
    }

    public Object details() {
        return details;
    }

    public Object producedKey() {
        return key;
    }

    public PlannerAmount outputPerPattern() {
        return amount;
    }

    public List<CompiledInput> inputs() {
        return inputs;
    }

    public List<GenericStack> outputs() {
        return outputs;
    }

    public List<GenericStack> grossOutputs() {
        return outputs;
    }

    public boolean fastSupported() {
        return true;
    }

    public String unsupportedReason() {
        return null;
    }
}
