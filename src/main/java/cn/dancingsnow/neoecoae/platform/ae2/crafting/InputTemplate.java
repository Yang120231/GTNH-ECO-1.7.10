package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/**
 * Represents a single "unit" of input for a slot in a crafting/processing pattern. How many units of input are required
 * for a pattern is returned by {@link IPatternDetails.IInput#getMultiplier()}.
 */
@com.github.bsideup.jabel.Desugar
public record InputTemplate(AEKey key, long amount) {}
