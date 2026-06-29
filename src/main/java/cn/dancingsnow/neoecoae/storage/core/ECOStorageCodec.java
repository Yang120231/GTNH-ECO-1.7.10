package cn.dancingsnow.neoecoae.storage.core;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class ECOStorageCodec {

    public static final int CURRENT_VERSION = 1;

    private ECOStorageCodec() {}

    public static void write(NBTTagCompound tag, ECOStorageBackend backend) {
        if (tag == null) {
            throw new IllegalArgumentException("Target tag must not be null");
        }
        if (backend == null) {
            throw new IllegalArgumentException("Backend must not be null");
        }
        tag.setInteger("version", CURRENT_VERSION);
        tag.setTag(
            "capacityPolicy",
            backend.getCapacityPolicy()
                .writeToNBT());
        tag.setTag(
            "used",
            backend.getUsed()
                .writeToNBT());
        tag.setLong("revision", backend.getRevision());

        NBTTagList entries = new NBTTagList();
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : backend.getEntriesForCodec()
            .entrySet()) {
            if (entry.getValue() == null || entry.getValue()
                .isZero()) {
                continue;
            }
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setTag(
                "key",
                entry.getKey()
                    .writeToNBT());
            entryTag.setTag(
                "amount",
                entry.getValue()
                    .writeToNBT());
            entries.appendTag(entryTag);
        }
        tag.setTag("entries", entries);
    }

    public static void read(NBTTagCompound tag, ECOStorageBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend must not be null");
        }
        if (tag == null || tag.hasNoTags()) {
            backend.loadFromCodec(
                ECOCapacityPolicy.infinite(),
                new LinkedHashMap<ECOStorageKey, ECOAmount>(),
                ECOAmount.ZERO,
                0L);
            return;
        }
        int version = tag.getInteger("version");
        if (version <= 0) {
            version = 1;
        }
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported ECO storage NBT version: " + version);
        }

        ECOCapacityPolicy policy = ECOCapacityPolicy.readFromNBT(tag.getCompoundTag("capacityPolicy"));
        Map<ECOStorageKey, ECOAmount> entries = new LinkedHashMap<ECOStorageKey, ECOAmount>();
        ECOAmount computedUsed = ECOAmount.ZERO;
        NBTTagList entryList = tag.getTagList("entries", 10);
        for (int i = 0; i < entryList.tagCount(); i++) {
            NBTTagCompound entryTag = entryList.getCompoundTagAt(i);
            ECOStorageKey key = ECOStorageKey.readFromNBT(entryTag.getCompoundTag("key"));
            ECOAmount amount = ECOAmount.readFromNBT(entryTag.getCompoundTag("amount"));
            if (amount.isZero()) {
                continue;
            }
            ECOAmount previous = entries.get(key);
            if (previous == null) {
                entries.put(key, amount);
            } else {
                entries.put(key, previous.add(amount));
            }
            computedUsed = computedUsed.add(amount);
        }

        ECOAmount storedUsed = tag.hasKey("used") ? ECOAmount.readFromNBT(tag.getCompoundTag("used")) : computedUsed;
        if (!storedUsed.equals(computedUsed)) {
            storedUsed = computedUsed;
        }
        backend.loadFromCodec(policy, entries, storedUsed, tag.getLong("revision"));
    }
}
