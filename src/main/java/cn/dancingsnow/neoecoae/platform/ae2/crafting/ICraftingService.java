package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

public interface ICraftingService {

    List<IPatternDetails> getCraftingFor(AEKey key);

    Set<AEKey> getCraftables(Predicate<AEKey> filter);

    boolean canEmitFor(AEKey key);
}
