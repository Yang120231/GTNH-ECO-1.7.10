package cn.dancingsnow.neoecoae.platform.registry;

/** Native registration descriptors transcribed from ECOTier at the locked business commit. */
public enum StorageTier implements StorageTierDescriptor {

    L4(1, 1L << 24),
    L6(2, 1L << 26),
    L9(3, 1L << 28);

    private final int tier;
    private final long bytes;

    StorageTier(int tier, long bytes) {
        this.tier = tier;
        this.bytes = bytes;
    }

    public int getTier() {
        return tier;
    }

    public long getStorageTotalBytes() {
        return bytes;
    }

    @Override
    public long getCPUTotalBytes() {
        return 1L << (24 + 2 * tier);
    }

    @Override
    public long getPowerStorageSize() {
        return tier == 1 ? 10_000_000L : tier == 2 ? 100_000_000L : 1_000_000_000L;
    }

    @Override
    public cn.dancingsnow.neoecoae.platform.minecraft.Component getPanelTierName() {
        return cn.dancingsnow.neoecoae.platform.minecraft.Component.literal(name());
    }

    public boolean supportsFeature(cn.dancingsnow.neoecoae.api.capability.ECOFeature feature) {
        switch (feature) {
            case NETWORK_SWITCH:
            case VIRTUAL_CRAFTING:
                return tier == 3;
            case INFINITE_STORAGE_HOST:
            case INFINITE_STORAGE_CELL:
                return this == L9;
            default:
                throw new IllegalArgumentException("Unknown feature: " + feature);
        }
    }
}
