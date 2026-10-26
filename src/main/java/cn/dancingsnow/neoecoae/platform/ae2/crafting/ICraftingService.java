package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.List;
import java.util.Set;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

public interface ICraftingService {

    List<IPatternDetails> getCraftingFor(AEKey key);

    Set<AEKey> getCraftables(AEKeyFilter filter);

    boolean canEmitFor(AEKey key);

    default List<ICraftingProvider> getProviders(IPatternDetails pattern) {
        throw new UnsupportedOperationException("A frozen planning snapshot has no live providers");
    }

    default void addLink(CraftingLink link) {
        throw new UnsupportedOperationException("A frozen planning snapshot cannot publish crafting links");
    }

    default long insertIntoCpus(AEKey key, long amount, appeng.api.config.Actionable mode) {
        throw new UnsupportedOperationException("A frozen planning snapshot cannot insert crafting outputs");
    }
}
