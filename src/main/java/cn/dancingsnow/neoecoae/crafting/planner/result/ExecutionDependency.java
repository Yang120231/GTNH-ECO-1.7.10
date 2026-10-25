package cn.dancingsnow.neoecoae.crafting.planner.result;

import java.util.Objects;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** A physical producer-to-consumer edge. Multiple edges for one key are intentional. */
@com.github.bsideup.jabel.Desugar
public record ExecutionDependency(IPatternDetails producer, IPatternDetails consumer, AEKey key) {

    public ExecutionDependency {
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(key, "key");
    }
}
