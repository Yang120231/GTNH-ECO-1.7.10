package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.UUID;

import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** UUID-based source link contract over the pinned native string identifier. */
public interface ICraftingLink {

    UUID getCraftingID();

    boolean isStandalone();

    boolean isCanceled();

    boolean isDone();

    void cancel();

    void writeToNBT(CompoundTag data);

    appeng.api.networking.crafting.ICraftingLink nativeLink();
}
