package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.nbt.CellComponents;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.CustomData;

public final class ECOInfiniteStorageMember {

    private static final String MEMBER_TAG = "neoecoae_infinite_member";
    private static final String DOMAIN_TAG = "neoecoae_infinite_domain";
    private static final String MIGRATION_TAG = "neoecoae_migration_id";
    private static final String MIGRATION_DOMAIN_TAG = "neoecoae_migration_domain";
    private static final String IDENTITY_TAG = "neoecoae_member_identity";

    private ECOInfiniteStorageMember() {}

    public static boolean isSealed(@Nullable ItemStack stack) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) return false;
        return isSealedData(CellComponents.readCustomData(stack));
    }

    static boolean isSealedData(CustomData data) {
        CompoundTag tag = readTag(data);
        return tag.hasUUID(MIGRATION_TAG) || tag.getBoolean(MEMBER_TAG);
    }

    /**
     * Read-only access: mutations must still copy the tag and replace the component.
     */
    @SuppressWarnings("deprecation")
    private static CompoundTag readTag(CustomData data) {
        return data.getUnsafe();
    }

    public static UUID identity(ItemStack stack) {
        CompoundTag tag = CellComponents.readCustomData(stack)
            .copyTag();
        if (tag.hasUUID(IDENTITY_TAG)) return tag.getUUID(IDENTITY_TAG);
        UUID identity = UUID.randomUUID();
        tag.putUUID(IDENTITY_TAG, identity);
        CellComponents.writeCustomData(stack, CustomData.of(tag));
        return identity;
    }

    public static Optional<UUID> getIdentity(@Nullable ItemStack stack) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack))
            return Optional.empty();
        CompoundTag tag = readTag(CellComponents.readCustomData(stack));
        return tag.hasUUID(IDENTITY_TAG) ? Optional.of(tag.getUUID(IDENTITY_TAG)) : Optional.empty();
    }

    public static boolean isMigrating(@Nullable ItemStack stack) {
        return getMigrationId(stack) != null;
    }

    public static @Nullable UUID getMigrationId(@Nullable ItemStack stack) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) return null;
        return migrationId(CellComponents.readCustomData(stack));
    }

    public static Optional<UUID> getMigrationDomainId(@Nullable ItemStack stack) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack))
            return Optional.empty();
        CompoundTag tag = readTag(CellComponents.readCustomData(stack));
        return tag.hasUUID(MIGRATION_DOMAIN_TAG) ? Optional.of(tag.getUUID(MIGRATION_DOMAIN_TAG)) : Optional.empty();
    }

    static @Nullable UUID migrationId(CustomData data) {
        CompoundTag tag = readTag(data);
        return tag.hasUUID(MIGRATION_TAG) ? tag.getUUID(MIGRATION_TAG) : null;
    }

    public static UUID beginMigration(ItemStack stack, UUID domain) {
        CompoundTag tag = CellComponents.readCustomData(stack)
            .copyTag();
        if (tag.hasUUID(MIGRATION_TAG)) {
            if (!tag.hasUUID(MIGRATION_DOMAIN_TAG) || !domain.equals(tag.getUUID(MIGRATION_DOMAIN_TAG))) {
                throw new IllegalStateException("Storage cell belongs to another migration");
            }
            return tag.getUUID(MIGRATION_TAG);
        }
        UUID id = UUID.randomUUID();
        tag.putUUID(MIGRATION_TAG, id);
        tag.putUUID(MIGRATION_DOMAIN_TAG, domain);
        CellComponents.writeCustomData(stack, CustomData.of(tag));
        return id;
    }

    public static boolean isMember(@Nullable ItemStack stack) {
        return stack != null && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)
            && isMemberData(CellComponents.readCustomData(stack));
    }

    static boolean isMemberData(CustomData data) {
        return readTag(data).getBoolean(MEMBER_TAG);
    }

    public static Optional<UUID> getDomainId(@Nullable ItemStack stack) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
            return Optional.empty();
        }
        return domainId(CellComponents.readCustomData(stack));
    }

    static Optional<UUID> domainId(CustomData data) {
        CompoundTag tag = readTag(data);
        if (!tag.getBoolean(MEMBER_TAG) || !tag.hasUUID(DOMAIN_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(DOMAIN_TAG));
    }

    public static boolean isMemberOf(@Nullable ItemStack stack, UUID domainId) {
        return getDomainId(stack).map(domainId::equals)
            .orElse(false);
    }

    public static void markMember(@Nullable ItemStack stack, UUID domainId) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
            return;
        }
        CompoundTag tag = CellComponents.readCustomData(stack)
            .copyTag();
        tag.putBoolean(MEMBER_TAG, true);
        tag.remove(MIGRATION_TAG);
        tag.remove(MIGRATION_DOMAIN_TAG);
        tag.putUUID(DOMAIN_TAG, domainId);
        if (!tag.hasUUID(IDENTITY_TAG)) tag.putUUID(IDENTITY_TAG, UUID.randomUUID());
        CellComponents.writeCustomData(stack, CustomData.of(tag));
    }

    public static void clearMember(@Nullable ItemStack stack) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
            return;
        }
        CompoundTag tag = CellComponents.readCustomData(stack)
            .copyTag();
        tag.remove(MEMBER_TAG);
        tag.remove(DOMAIN_TAG);
        // Identities identify one membership lifetime. Keeping them on an ordinary cell makes
        // creative/NBT copies indistinguishable during the next restore.
        tag.remove(IDENTITY_TAG);
        if (tag.isEmpty()) {
            CellComponents.clearCustomData(stack);
        } else {
            CellComponents.writeCustomData(stack, CustomData.of(tag));
        }
    }

    public static void clearStoredContents(@Nullable ItemStack stack) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) {
            return;
        }
        var inventory = ECOStorageCells.getCellInventory(stack, null);
        if (inventory instanceof IECOStorageMigrationCell migrationCell) {
            migrationCell.clearMigrationStacks();
        } else if (inventory != null) {
            var available = inventory.getAvailableStacks();
            for (var entry : available) {
                inventory.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, IActionSource.empty());
            }
            inventory.persist();
        }
        // Standard ECO cells use this component. Removing it is also a safe fallback if no handler was available.
        CellComponents.clearStacks(stack);
    }
}
