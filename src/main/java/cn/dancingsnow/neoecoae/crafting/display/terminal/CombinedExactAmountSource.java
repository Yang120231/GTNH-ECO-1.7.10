package cn.dancingsnow.neoecoae.crafting.display.terminal;

import java.util.function.BiConsumer;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Emits the full visible contribution and its exact values in one traversal, on the server thread. */
public interface CombinedExactAmountSource extends ExactAmountSource {

    void neoecoae$listWithExactAmounts(KeyCounter out, BiConsumer<AEKey, ExactAmount> visitor);
}
