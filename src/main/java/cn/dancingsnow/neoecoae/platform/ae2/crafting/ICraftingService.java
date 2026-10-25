package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.List;
import java.util.Set;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

public interface ICraftingService {

    List<IPatternDetails> getCraftingFor(AEKey key);

    Set<AEKey> getCraftables(AEKeyFilter filter);

    boolean canEmitFor(AEKey key);
}
