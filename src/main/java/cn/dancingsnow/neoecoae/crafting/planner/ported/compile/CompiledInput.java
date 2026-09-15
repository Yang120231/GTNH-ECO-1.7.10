package cn.dancingsnow.neoecoae.crafting.planner.ported.compile;

import cn.dancingsnow.neoecoae.crafting.planner.ported.solve.PlannerAmount;

public final class CompiledInput {

    private final Object key;
    private final PlannerAmount amount;

    public CompiledInput(Object key, long amount) {
        this.key = key;
        this.amount = PlannerAmount.of(amount);
    }

    public Object key() {
        return key;
    }

    public PlannerAmount amountPerPattern() {
        return amount;
    }

    public boolean fastSupported() {
        return true;
    }

    public String unsupportedReason() {
        return null;
    }
}
