package cn.dancingsnow.neoecoae.crafting.planner.graph;

import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** A candidate pattern's producer -> logical required-input relationship. */
@com.github.bsideup.jabel.Desugar
public record CraftingGraphEdge(AEKey producer, AEKey requiredInput, CompiledPattern pattern, CompiledInput input) {}
