package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

@com.github.bsideup.jabel.Desugar
public record CraftingJobStatus(GenericStack crafting, long totalItems, long progress, long elapsedTimeNanos) {}
