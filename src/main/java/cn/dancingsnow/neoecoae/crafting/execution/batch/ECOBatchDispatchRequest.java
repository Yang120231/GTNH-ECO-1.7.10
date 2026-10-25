package cn.dancingsnow.neoecoae.crafting.execution.batch;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Provider-facing request for exactly one materialized batch. */
@com.github.bsideup.jabel.Desugar
public record ECOBatchDispatchRequest(ECOPatternIdentity identity, ECOBatchExecutionView executionView,
    KeyCounter[] inputCounters, KeyCounter outputCounter, KeyCounter remainderCounter, long craftCount, World level,
    UUID jobId) {

    public ECOBatchDispatchRequest {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(executionView, "executionView");
        Objects.requireNonNull(inputCounters, "inputCounters");
        Objects.requireNonNull(outputCounter, "outputCounter");
        Objects.requireNonNull(remainderCounter, "remainderCounter");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(jobId, "jobId");
        if (craftCount <= 0L) throw new IllegalArgumentException("craftCount must be positive");
    }
}
