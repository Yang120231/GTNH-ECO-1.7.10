package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;

/** The tier metadata used by the currently imported storage contracts; addons can supply their own descriptors. */
public interface StorageTierDescriptor {

    int getTier();

    long getStorageTotalBytes();

    long getCPUTotalBytes();

    boolean supportsFeature(ECOFeature feature);

    default double getComponentIdlePower() {
        return 16.0D;
    }

    default int compareTo(StorageTierDescriptor other) {
        return Integer.compare(getTier(), other.getTier());
    }

    default boolean supportsComponentTier(StorageTierDescriptor other) {
        return compareTo(other) >= 0;
    }

    long getPowerStorageSize();

    default cn.dancingsnow.neoecoae.platform.minecraft.Component getPanelTierName() {
        return cn.dancingsnow.neoecoae.platform.minecraft.Component.literal("L" + getTier());
    }
}
