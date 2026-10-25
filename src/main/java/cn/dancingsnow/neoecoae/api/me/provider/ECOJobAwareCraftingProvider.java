package cn.dancingsnow.neoecoae.api.me.provider;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Ordinary one-copy ownership transfer that retains the calling CPU's task identity. */
public interface ECOJobAwareCraftingProvider extends ICraftingProvider {

    boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, @Nullable UUID craftingJobId);
}
