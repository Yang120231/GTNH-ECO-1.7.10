package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Lightweight result of extracting one craft from the CPU inventory. */
@com.github.bsideup.jabel.Desugar
public record ECOExtractedCraft(KeyCounter[] craftingContainer, KeyCounter expectedOutputs,
    KeyCounter expectedContainerItems, double patternPower) {}
