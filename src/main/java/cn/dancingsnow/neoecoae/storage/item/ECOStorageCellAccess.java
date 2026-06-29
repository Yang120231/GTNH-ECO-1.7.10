package cn.dancingsnow.neoecoae.storage.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOCapacityPolicy;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;

public final class ECOStorageCellAccess {

    private static final String TAG_STORAGE = "ECOStorage";
    private static final String TAG_TIER = "ECOTier";
    private static final String TAG_CHANNEL = "ECOChannel";

    private ECOStorageCellAccess() {}

    public static ECOStorageBackend load(ItemStack stack) {
        ECOStorageBackend backend = new ECOStorageBackend(capacityFor(stack));
        if (ECOStorageCellMetadata.hasNonPortableState(stack)) {
            return backend;
        }
        if (stack != null && stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(TAG_STORAGE)) {
            backend.readFromNBT(
                stack.getTagCompound()
                    .getCompoundTag(TAG_STORAGE));
        }
        return backend;
    }

    public static void save(ItemStack stack, ECOStorageBackend backend) {
        if (stack == null || backend == null) {
            return;
        }
        if (ECOStorageCellMetadata.hasNonPortableState(stack)) {
            ECOStorageCellMetadata.writeSummary(stack, backend.getUsed().toLongSaturated(), backend.getTypeCount());
            return;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        NBTTagCompound storage = new NBTTagCompound();
        backend.writeToNBT(storage);
        root.setTag(TAG_STORAGE, storage);
    }

    public static void clearStorage(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null) {
            return;
        }
        stack.getTagCompound().removeTag(TAG_STORAGE);
    }

    public static ECOCapacityPolicy capacityFor(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof IECOStorageMatrixItem) {
            return ECOCapacityPolicy.finite(((IECOStorageMatrixItem) stack.getItem()).getDisplayBytes(stack));
        }
        return ECOCapacityPolicy.infinite();
    }

    public static void writeCellIdentity(ItemStack stack, String channel, String tier) {
        if (stack == null) {
            return;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        root.setString(TAG_CHANNEL, channel);
        root.setString(TAG_TIER, tier);
    }

    public static String readTier(ItemStack stack, String fallback) {
        if (stack != null && stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(TAG_TIER)) {
            return stack.getTagCompound()
                .getString(TAG_TIER);
        }
        return fallback;
    }

    public static String readChannel(ItemStack stack, String fallback) {
        if (stack != null && stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(TAG_CHANNEL)) {
            return stack.getTagCompound()
                .getString(TAG_CHANNEL);
        }
        return fallback;
    }

    public static ECOAmount getUsed(ItemStack stack) {
        return load(stack).getUsed();
    }
}
