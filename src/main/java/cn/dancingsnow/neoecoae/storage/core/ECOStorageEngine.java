package cn.dancingsnow.neoecoae.storage.core;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/** Common inventory contract shared by portable cells and world-owned infinite domains. */
public interface ECOStorageEngine {

    ECOAmount insert(ECOStorageKey key, ECOAmount amount, boolean simulate);

    ECOAmount extract(ECOStorageKey key, ECOAmount amount, boolean simulate);

    ECOAmount getAmount(ECOStorageKey key);

    Map<ECOStorageKey, ECOAmount> getEntriesView();

    ECOStorageSnapshot snapshot();

    int getTypeCount();

    boolean isEmpty();

    long getRevision();

    ECOAmount getUsed();

    ECOCapacityPolicy getCapacityPolicy();

    boolean isHealthy();

    String getFailureReason();

    void writeToNBT(NBTTagCompound tag);
}
