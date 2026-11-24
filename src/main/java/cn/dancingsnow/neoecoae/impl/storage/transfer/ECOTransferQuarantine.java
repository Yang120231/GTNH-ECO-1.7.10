package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.math.BigInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;

/** Inert offers without a receipt; never a retry, refund or drop source. */
public final class ECOTransferQuarantine {

    public static final long UNKNOWN = -1L;
    private static final Logger LOG = LogManager.getLogger(ECOTransferQuarantine.class);
    private final ECOExactInventory offers = new ECOExactInventory(ignored -> {});

    public ECOTransferQuarantine() {
        offers.setEnabled(true);
    }

    public long attempt(AEKey key, long offered, LongSupplier transfer) {
        try {
            return check(transfer.getAsLong(), offered);
        } catch (RuntimeException failure) {
            record(key, BigInteger.valueOf(offered), failure);
            return UNKNOWN;
        }
    }

    public Receipt attemptWithPrefix(AEKey key, long offered, LongSupplier transfer) {
        try {
            return new Receipt(check(transfer.getAsLong(), offered), 0L);
        } catch (RuntimeException failure) {
            long confirmed = failure instanceof ConfirmedPrefix prefix && prefix.amount > 0 && prefix.amount < offered
                ? prefix.amount
                : 0L;
            long uncertain = offered - confirmed;
            record(key, BigInteger.valueOf(uncertain), failure);
            return new Receipt(confirmed, uncertain);
        }
    }

    @com.github.bsideup.jabel.Desugar
    public record Receipt(long confirmed, long uncertain) {}

    /** Only used by composed local adapters that have already received a valid child receipt. */
    public static final class ConfirmedPrefix extends RuntimeException {

        private final long amount;

        public ConfirmedPrefix(long amount, RuntimeException failure) {
            super("Following transfer failed after a confirmed prefix", failure);
            this.amount = amount;
        }
    }

    public @Nullable BigInteger attemptExact(AEKey key, BigInteger offered, Supplier<BigInteger> transfer) {
        try {
            BigInteger accepted = transfer.get();
            if (accepted == null || accepted.signum() < 0 || accepted.compareTo(offered) > 0)
                throw new IllegalStateException("Invalid exact transfer receipt: " + accepted + " for " + offered);
            return accepted;
        } catch (RuntimeException failure) {
            record(key, offered, failure);
            return null;
        }
    }

    private void record(AEKey key, BigInteger offered, RuntimeException failure) {
        offers.restore(JdkCompat.mapOf(key, offered));
        LOG.warn("Quarantined an unconfirmed ECO transfer of {} x {}", offered, key, failure);
    }

    public static long check(long accepted, long offered) {
        if (accepted < 0 || accepted > offered)
            throw new IllegalStateException("Invalid transfer receipt: " + accepted + " for " + offered);
        return accepted;
    }

    public static long unavailable(long accepted, long offered) {
        return accepted == UNKNOWN ? offered : accepted;
    }

    public boolean isEmpty() {
        return offers.list.isEmpty();
    }

    public boolean contains(AEKey key) {
        return offers.list.get(key) > 0L;
    }

    public void save(CompoundTag tag, String name, HolderLookup.Provider registries) {
        if (isEmpty()) tag.remove(name);
        else tag.put(name, offers.writeToNBT(registries));
    }

    public void load(CompoundTag tag, String name, HolderLookup.Provider registries) {
        offers.readFromNBT(tag.getList(name, Tag.TAG_COMPOUND), registries);
    }
}
