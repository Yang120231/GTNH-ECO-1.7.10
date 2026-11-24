package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferQuarantine;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;

/** Transfers additional recipe costs into the same recoverable ownership ledger as CPU inputs. */
final class LargeWorkstationExtraInputs {

    private LargeWorkstationExtraInputs() {}

    static boolean acquire(KeyCounter missing, KeyCounter owned, MEStorage storage, IActionSource source,
        Runnable changed) {
        for (var extra : ECOFastPathStacks.copyCounter(missing)) {
            long extracted = storage.extract(extra.what(), extra.amount(), Actionable.MODULATE, source);
            if (extracted < 0 || extracted > extra.amount())
                throw new IllegalStateException("Invalid extra input extraction");
            if (extracted > 0) {
                owned.add(extra.what(), extracted);
                missing.remove(extra.what(), extracted);
                changed.run();
            }
        }
        for (var entry : missing) if (entry.getLongValue() > 0) return false;
        return true;
    }

    static boolean returnOwned(KeyCounter owned, MEStorage storage, IActionSource source,
        ECOTransferQuarantine quarantine, Runnable changed) {
        for (var stack : ECOFastPathStacks.copyCounter(owned)) {
            long inserted = quarantine.attempt(
                stack.what(),
                stack.amount(),
                () -> storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source));
            if (inserted != 0L) {
                owned.remove(stack.what(), ECOTransferQuarantine.unavailable(inserted, stack.amount()));
                changed.run();
            }
            if (inserted >= 0L && inserted < stack.amount()) return false;
        }
        return true;
    }
}
