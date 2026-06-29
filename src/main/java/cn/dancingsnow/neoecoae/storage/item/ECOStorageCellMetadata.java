package cn.dancingsnow.neoecoae.storage.item;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public final class ECOStorageCellMetadata {

    private static final String TAG_DISK_ID = "ECODiskId";
    private static final String TAG_MODE = "ECOStorageMode";
    private static final String TAG_HOST_DOMAIN_ID = "ECOHostDomainId";
    private static final String TAG_MEMBER_INDEX = "ECOMemberIndex";
    private static final String TAG_SUMMARY_USED = "ECOSummaryUsed";
    private static final String TAG_SUMMARY_TYPES = "ECOSummaryTypes";

    private ECOStorageCellMetadata() {}

    public static UUID getDiskId(ItemStack stack) {
        NBTTagCompound tag = getTag(stack);
        if (tag == null || !tag.hasKey(TAG_DISK_ID)) {
            return null;
        }
        return readUuid(tag.getString(TAG_DISK_ID));
    }

    public static UUID getOrCreateDiskId(ItemStack stack) {
        UUID diskId = getDiskId(stack);
        if (diskId != null) {
            return diskId;
        }
        UUID created = UUID.randomUUID();
        tag(stack).setString(TAG_DISK_ID, created.toString());
        if (!tag(stack).hasKey(TAG_MODE)) {
            setMode(stack, ECOStorageCellMode.PORTABLE);
        }
        return created;
    }

    public static ECOStorageCellMode getMode(ItemStack stack) {
        NBTTagCompound tag = getTag(stack);
        return tag == null ? ECOStorageCellMode.PORTABLE : ECOStorageCellMode.fromId(tag.getString(TAG_MODE));
    }

    public static void setMode(ItemStack stack, ECOStorageCellMode mode) {
        tag(stack).setString(TAG_MODE, (mode == null ? ECOStorageCellMode.PORTABLE : mode).getId());
    }

    public static boolean isPortable(ItemStack stack) {
        return getMode(stack) == ECOStorageCellMode.PORTABLE;
    }

    public static boolean hasNonPortableState(ItemStack stack) {
        ECOStorageCellMode mode = getMode(stack);
        return mode == ECOStorageCellMode.MIGRATING || mode == ECOStorageCellMode.DOMAIN_MEMBER;
    }

    public static void markMigrating(ItemStack stack, UUID domainId, int memberIndex) {
        bind(stack, ECOStorageCellMode.MIGRATING, domainId, memberIndex);
    }

    public static void markDomainMember(ItemStack stack, UUID domainId, int memberIndex) {
        bind(stack, ECOStorageCellMode.DOMAIN_MEMBER, domainId, memberIndex);
    }

    public static void clearDomainBinding(ItemStack stack) {
        NBTTagCompound tag = tag(stack);
        tag.removeTag(TAG_HOST_DOMAIN_ID);
        tag.removeTag(TAG_MEMBER_INDEX);
        tag.removeTag(TAG_SUMMARY_USED);
        tag.removeTag(TAG_SUMMARY_TYPES);
        setMode(stack, ECOStorageCellMode.PORTABLE);
    }

    public static UUID getHostDomainId(ItemStack stack) {
        NBTTagCompound tag = getTag(stack);
        if (tag == null || !tag.hasKey(TAG_HOST_DOMAIN_ID)) {
            return null;
        }
        return readUuid(tag.getString(TAG_HOST_DOMAIN_ID));
    }

    public static int getMemberIndex(ItemStack stack) {
        NBTTagCompound tag = getTag(stack);
        return tag == null ? -1 : tag.getInteger(TAG_MEMBER_INDEX);
    }

    public static void writeSummary(ItemStack stack, long used, int types) {
        NBTTagCompound tag = tag(stack);
        tag.setLong(TAG_SUMMARY_USED, Math.max(0L, used));
        tag.setInteger(TAG_SUMMARY_TYPES, Math.max(0, types));
    }

    public static long getSummaryUsed(ItemStack stack) {
        NBTTagCompound tag = getTag(stack);
        return tag == null ? 0L : tag.getLong(TAG_SUMMARY_USED);
    }

    public static int getSummaryTypes(ItemStack stack) {
        NBTTagCompound tag = getTag(stack);
        return tag == null ? 0 : tag.getInteger(TAG_SUMMARY_TYPES);
    }

    private static void bind(ItemStack stack, ECOStorageCellMode mode, UUID domainId, int memberIndex) {
        NBTTagCompound tag = tag(stack);
        tag.setString(TAG_HOST_DOMAIN_ID, domainId.toString());
        tag.setInteger(TAG_MEMBER_INDEX, memberIndex);
        setMode(stack, mode);
    }

    private static NBTTagCompound getTag(ItemStack stack) {
        return stack == null ? null : stack.getTagCompound();
    }

    private static NBTTagCompound tag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    private static UUID readUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
