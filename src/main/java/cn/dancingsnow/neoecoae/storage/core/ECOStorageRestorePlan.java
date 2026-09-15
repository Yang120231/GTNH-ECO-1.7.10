package cn.dancingsnow.neoecoae.storage.core;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/** Builds target snapshots without mutating the source or physical cells. */
public final class ECOStorageRestorePlan {

    private ECOStorageRestorePlan() {}

    public static Map<UUID, ECOStorageBackend> create(ECOStorageBackend source, Map<UUID, ECOStorageBackend> targets) {
        if (source == null || !source.isHealthy() || targets.isEmpty()) return null;
        Map<UUID, ECOStorageBackend> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, ECOStorageBackend> entry : targets.entrySet()) {
            ECOStorageBackend target = entry.getValue();
            if (entry.getKey() == null || target == null
                || !target.isHealthy()
                || target.getCapacityPolicy()
                    .isInfinite())
                return null;
            NBTTagCompound saved = new NBTTagCompound();
            target.writeToNBT(saved);
            ECOStorageBackend copy = new ECOStorageBackend(target.getCapacityPolicy());
            copy.setAcceptedChannel(target.getAcceptedChannel());
            copy.setMaximumTypes(target.getMaximumTypes());
            copy.readFromNBT(saved);
            result.put(entry.getKey(), copy);
        }
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : source.getEntriesView()
            .entrySet()) {
            ECOAmount remaining = entry.getValue();
            if (remaining.toBigInteger()
                .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return null;
            for (ECOStorageBackend target : result.values()) {
                remaining = remaining.subtract(target.insert(entry.getKey(), remaining, false));
                if (remaining.isZero()) break;
            }
            if (!remaining.isZero()) return null;
        }
        BigInteger used = BigInteger.ZERO;
        BigInteger capacity = BigInteger.ZERO;
        for (ECOStorageBackend target : result.values()) {
            used = used.add(
                target.getUsed()
                    .toBigInteger());
            capacity = capacity.add(
                target.getCapacityPolicy()
                    .getCapacity()
                    .toBigInteger());
        }
        BigInteger reserve = capacity.divide(BigInteger.valueOf(100L))
            .multiply(BigInteger.valueOf(5L))
            .max(BigInteger.ONE);
        return used.compareTo(capacity.subtract(reserve)) <= 0 ? result : null;
    }
}
