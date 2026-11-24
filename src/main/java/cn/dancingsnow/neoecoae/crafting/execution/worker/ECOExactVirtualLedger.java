package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferQuarantine;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Durable custody for one exact virtual lane. External long APIs only see bounded windows. */
public final class ECOExactVirtualLedger {

    private final BigInteger crafts;
    private final ECOExactInventory inputs = inventory();
    private final ECOExactInventory outputs = inventory();

    private static ECOExactInventory inventory() {
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        return inventory;
    }

    public ECOExactVirtualLedger(BigInteger crafts, List<GenericStack> inputs, List<GenericStack> outputs,
        List<GenericStack> remaining) {
        if (crafts.signum() <= 0) throw new IllegalArgumentException("Invalid exact virtual count");
        this.crafts = crafts;
        this.inputs.restore(ECOExactInventory.totals(inputs, crafts));
        this.outputs.restore(ECOExactInventory.totals(outputs, crafts));
        this.outputs.restore(ECOExactInventory.totals(remaining, crafts));
    }

    private ECOExactVirtualLedger(BigInteger crafts) {
        if (crafts.signum() <= 0) throw new IllegalArgumentException("Invalid saved exact virtual count");
        this.crafts = crafts;
    }

    public BigInteger crafts() {
        return crafts;
    }

    public Map<AEKey, BigInteger> snapshot(boolean output) {
        return (output ? outputs : inputs).snapshot();
    }

    /** Exact delivery path used when the destination can accept more than AE2's long-sized windows. */
    public boolean drainExact(boolean output, Function<BigInteger, BigInteger> window,
        BiFunction<AEKey, BigInteger, BigInteger> insert, ECOTransferQuarantine quarantine, Runnable changed) {
        var inventory = output ? outputs : inputs;
        for (var entry : inventory.snapshot()
            .entrySet()) {
            AEKey key = entry.getKey();
            BigInteger offered = window.apply(entry.getValue());
            BigInteger accepted = quarantine.attemptExact(key, offered, () -> insert.apply(key, offered));
            BigInteger unavailable = accepted == null ? offered : accepted;
            if (unavailable.signum() > 0) {
                inventory.debit(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, unavailable));
                changed.run();
            }
            if (accepted == null) return false;
        }
        return inventory.snapshot()
            .isEmpty();
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("crafts", crafts.toString());
        tag.put("inputs", inputs.writeToNBT(registries));
        tag.put("outputs", outputs.writeToNBT(registries));
        return tag;
    }

    public static ECOExactVirtualLedger read(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new ECOExactVirtualLedger(new BigInteger(tag.getString("crafts")));
        ledger.inputs.readFromNBT(tag.getList("inputs", 10), registries);
        ledger.outputs.readFromNBT(tag.getList("outputs", 10), registries);
        return ledger;
    }
}
