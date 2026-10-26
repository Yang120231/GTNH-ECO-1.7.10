package cn.dancingsnow.neoecoae.crafting.execution;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** One candidate and its resolved, still-virtual input set for provider dispatch. */
@com.github.bsideup.jabel.Desugar
record ECOCraftingDispatchRequest(ExecutingCraftingJob job, ECOExecutionRuntime.DispatchCandidate candidate,
    IPatternDetails pattern, KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders, long allowedCrafts,
    ListCraftingInventory inventory, World level) {}
