package cn.dancingsnow.neoecoae.gui.common;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;

public final class HostPanelTier {

    @Nullable
    public static StorageTierDescriptor resolve(@Nullable StorageTierDescriptor tier, int legacyTier) {
        return tier != null ? tier : switch (legacyTier) {
            case 1 -> StorageTiers.L4;
            case 2 -> StorageTiers.L6;
            case 3 -> StorageTiers.L9;
            default -> null;
        };
    }

    public static Component name(@Nullable StorageTierDescriptor tier, int legacyTier) {
        StorageTierDescriptor resolved = resolve(tier, legacyTier);
        return resolved == null ? Component.literal("L" + legacyTier) : resolved.getPanelTierName();
    }

    private HostPanelTier() {}
}
