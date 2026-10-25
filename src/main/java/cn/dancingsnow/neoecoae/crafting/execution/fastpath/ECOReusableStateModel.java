package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import java.util.List;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/** A composable proof for reusable input state across an entire batch. */
public interface ECOReusableStateModel {

    long maxBatchSize();

    List<GenericStack> batchInputs(List<GenericStack> ordinaryInputs, long crafts);

    List<GenericStack> batchRemainders(List<GenericStack> ordinaryRemainders, long crafts);

    boolean requiresSecondStepProof();

    boolean sameTransition(ECOReusableStateModel other);
}
