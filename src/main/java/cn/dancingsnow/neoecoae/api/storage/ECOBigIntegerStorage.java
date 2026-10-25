package cn.dancingsnow.neoecoae.api.storage;

import java.math.BigInteger;
import java.util.Objects;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;

/** Optional exact-amount insertion for storage that can represent quantities beyond AE2's long API. */
public interface ECOBigIntegerStorage {

    BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source);

    /** Inserts through the regular long API unless this is a request the long API cannot represent. */
    static BigInteger insert(MEStorage storage, AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
        if (amount.signum() < 0) throw new IllegalArgumentException("Amount must not be negative");
        if (amount.signum() == 0) return BigInteger.ZERO;

        BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
        if (amount.compareTo(max) <= 0) {
            return Result
                .checkedResult(amount, BigInteger.valueOf(storage.insert(what, amount.longValueExact(), mode, source)));
        }
        if (storage instanceof ECOBigIntegerStorage exact) {
            return Result.checkedResult(amount, exact.insertBigInteger(what, amount, mode, source));
        }

        long inserted = storage.insert(what, Long.MAX_VALUE, mode, source);
        return Result.checkedResult(amount, BigInteger.valueOf(inserted));
    }

    final class Result {

        private Result() {}

        private static BigInteger checkedResult(BigInteger offered, BigInteger inserted) {
            if (inserted == null || inserted.signum() < 0 || inserted.compareTo(offered) > 0) {
                throw new IllegalStateException(
                    "Invalid BigInteger storage insertion result: " + inserted + " for " + offered);
            }
            return inserted;
        }
    }
}
