package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** Immutable exact stock shared by calculation probes and candidate retries. */
public final class PlannerInventorySnapshot {

    private final AEKey[] keys;
    private final PlannerAmount[] amounts;
    private final java.util.Set<AEKey> unboundedKeys;

    private PlannerInventorySnapshot(AEKey[] keys, PlannerAmount[] amounts, java.util.Set<AEKey> unboundedKeys) {
        this.keys = keys;
        this.amounts = amounts;
        this.unboundedKeys = unboundedKeys;
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory) {
        return of(inventory, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf());
    }

    public static PlannerInventorySnapshot of(KeyCounter inventory, java.util.Set<AEKey> unboundedKeys) {
        java.util.List<AEKey> keys = new java.util.ArrayList<>();
        java.util.List<PlannerAmount> amounts = new java.util.ArrayList<>();
        for (var entry : inventory) {
            if (entry.getLongValue() <= 0) continue;
            keys.add(entry.getKey());
            amounts.add(PlannerAmount.of(entry.getLongValue()));
        }
        return new PlannerInventorySnapshot(
            keys.toArray(new AEKey[0]),
            amounts.toArray(new PlannerAmount[0]),
            unboundedKeys);
    }

    void initialize(PlannerCounter target) {
        for (int i = 0; i < keys.length; i++) target.set(keys[i], amounts[i]);
        unboundedKeys.forEach(target::setUnbounded);
    }

    public boolean isUnbounded(AEKey key) {
        return unboundedKeys.contains(key);
    }

    /** Independent AE2 counter for APIs that still require long-valued stock. */
    public KeyCounter toKeyCounter() {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < keys.length; i++) result.set(keys[i], amounts[i].longValueExact());
        unboundedKeys.forEach(key -> result.set(key, Long.MAX_VALUE));
        return result;
    }
}
