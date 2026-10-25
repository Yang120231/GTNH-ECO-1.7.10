package cn.dancingsnow.neoecoae.crafting.execution.batch;

import java.util.List;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails.PatternInputSink;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Ephemeral execution description for exactly one materialized batch. */
public interface ECOBatchExecutionView {

    IPatternDetails originalPattern();

    long craftCount();

    List<List<cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack>> scaledInputs();

    List<cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack> scaledOutputs();

    List<cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack> scaledRemainders();

    boolean supportsExternalInputPush();

    void pushInputs(KeyCounter[] counters, PatternInputSink sink);

    World level();
}
