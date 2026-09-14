package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ECOPlannerRecipe {

    private final ECOItemKey output;
    private final long outputAmount;
    private final Map<ECOItemKey, Long> inputs;

    public ECOPlannerRecipe(ECOItemKey output, long outputAmount, Map<ECOItemKey, Long> inputs) {
        if (output == null || outputAmount <= 0) throw new IllegalArgumentException("invalid output");
        this.output = output;
        this.outputAmount = outputAmount;
        this.inputs = ECORecipe.freeze(inputs == null ? new LinkedHashMap<ECOItemKey, Long>() : inputs);
    }

    public ECOItemKey output() {
        return output;
    }

    public long outputAmount() {
        return outputAmount;
    }

    public Map<ECOItemKey, Long> inputs() {
        return inputs;
    }
}
