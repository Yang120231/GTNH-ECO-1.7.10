package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;

/** The tier metadata used by the currently imported storage contracts; addons can supply their own descriptors. */
public interface StorageTierDescriptor {

    int getTier();

    long getStorageTotalBytes();

    default double getStorageIdleDrain() {
        return (double) getStorageTotalBytes() / (1 << 20);
    }

    long getCPUTotalBytes();

    boolean supportsFeature(ECOFeature feature);

    default cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition getBuildDefinition(
        cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind kind) {
        return null;
    }

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

    default int getCPUThreads() {
        return getTier() == 1 ? 4 : getTier() == 2 ? 16 : 64;
    }

    default int getCPUAccelerators() {
        return getTier() == 1 ? 64 : getTier() == 2 ? 192 : 576;
    }

    default int getCrafterParallel() {
        return getTier() == 1 ? 24 : getTier() == 2 ? 72 : 256;
    }

    default int getOverclockedCrafterParallel() {
        return getTier() == 1 ? 32 : getTier() == 2 ? 96 : 384;
    }

    default int getOverclockedCrafterQueueMultiply() {
        return 2 << getTier();
    }

    default int getOverclockedCrafterPowerMultiply() {
        return 2 << getTier();
    }
}
