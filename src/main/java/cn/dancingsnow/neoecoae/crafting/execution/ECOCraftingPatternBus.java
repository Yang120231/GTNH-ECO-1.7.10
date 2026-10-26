package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.UUID;

import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Physical ECO pattern-bus single-craft transport, independent from its block UI. */
public interface ECOCraftingPatternBus extends ICraftingProvider {

    boolean pushPattern(ECOExtractedPatternExecution execution, UUID job);

    boolean pushPatternSlow(IPatternDetails pattern, KeyCounter[] inputs, UUID job);
}
