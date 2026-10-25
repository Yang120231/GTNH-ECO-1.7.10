package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.Map;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** High-version numerical plan contract; submitting it requires the target CPU bridge. */
public interface ICraftingPlan {

    GenericStack finalOutput();

    long bytes();

    boolean simulation();

    boolean multiplePaths();

    KeyCounter usedItems();

    KeyCounter emittedItems();

    KeyCounter missingItems();

    Map<IPatternDetails, Long> patternTimes();
}
