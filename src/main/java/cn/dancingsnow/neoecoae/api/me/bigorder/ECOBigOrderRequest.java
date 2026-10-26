package cn.dancingsnow.neoecoae.api.me.bigorder;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.execution.bigorder.ECOBigCraftingOrder;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Parent metadata is deliberately separate from AE2's long-only ICraftingPlan. */
@com.github.bsideup.jabel.Desugar
public record ECOBigOrderRequest(AEKey goal, BigInteger requested, boolean forced, ECOPlannerOptions options,
    Map<AEKey, BigInteger> pendingPreview) {

    public ECOBigOrderRequest(AEKey goal, BigInteger requested, boolean forced, ECOPlannerOptions options) {
        this(goal, requested, forced, options, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
    }

    /** Creates a waiting parent order; executable task vectors are planned later as bounded child segments. */
    public static ECOBigOrderRequest fromPlanningResult(ECOPlanningResult result, boolean forced,
        ECOPlannerOptions options) {
        java.util.Objects.requireNonNull(result, "result");
        if (result.plan() == null) throw new IllegalArgumentException("Missing planning projection");
        Map<AEKey, BigInteger> preview = new LinkedHashMap<>();
        result.trace()
            .nodes()
            .forEach(node -> {
                if (node.key() != null && node.exactToCraft()
                    .signum() > 0) {
                    preview.merge(node.key(), node.exactToCraft(), BigInteger::max);
                }
            });
        var output = result.plan()
            .finalOutput();
        return new ECOBigOrderRequest(output.what(), BigInteger.valueOf(output.amount()), forced, options, preview);
    }

    private static final ThreadLocal<Submission> SUBMISSION = new ThreadLocal<>();

    @com.github.bsideup.jabel.Desugar
    private record Submission(ICraftingPlan carrier, ECOBigOrderRequest request) {}

    public ECOBigOrderRequest {
        java.util.Objects.requireNonNull(goal);
        java.util.Objects.requireNonNull(options);

        ECOBigCraftingOrder.checked(requested);
        if (requested.signum() <= 0) throw new IllegalArgumentException("Empty order");
    }

    public ICraftingPlan carrier() {
        return new cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan(
            new GenericStack(
                goal,
                requested.min(BigInteger.valueOf(Long.MAX_VALUE))
                    .longValueExact()),
            0,
            false,
            false,
            new KeyCounter(),
            new KeyCounter(),
            new KeyCounter(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
    }

    public <T> T submit(java.util.function.Function<ICraftingPlan, T> submitter) {
        var carrier = carrier();
        var previous = SUBMISSION.get();
        SUBMISSION.set(new Submission(carrier, this));
        try {
            return submitter.apply(carrier);
        } finally {
            if (previous == null) SUBMISSION.remove();
            else SUBMISSION.set(previous);
        }
    }

    public static ECOBigOrderRequest forSubmission(ICraftingPlan plan) {
        var submission = SUBMISSION.get();
        return submission != null && submission.carrier == plan ? submission.request : null;
    }
}
