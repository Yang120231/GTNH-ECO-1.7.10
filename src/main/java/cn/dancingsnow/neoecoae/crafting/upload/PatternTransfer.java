package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.NeoECOAE;

/**
 * Moves exactly one encoded pattern between two physical slots.
 *
 * <p>
 * Both ends are read back. The source is restored only after confirming that the target received nothing; a partial
 * or unexpected target state is reported as {@link Status#INDETERMINATE} and never compensated blindly.
 * </p>
 */
public final class PatternTransfer {

    public enum Status {
        SUCCESS,
        REJECTED,
        INDETERMINATE
    }

    public static final class Result {

        public final Status status;
        public final String reason;

        private Result(Status status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public boolean succeeded() {
            return status == Status.SUCCESS;
        }
    }

    public static final String REASON_SOURCE_CHANGED = "gui.neoecoae.pattern_upload.reason.source_changed";
    public static final String REASON_TARGET_REJECTED = "gui.neoecoae.pattern_upload.reason.target_rejected";
    public static final String REASON_TARGET_OCCUPIED = "gui.neoecoae.pattern_upload.reason.target_occupied";
    public static final String REASON_OFFLINE = "gui.neoecoae.pattern_upload.reason.offline";
    public static final String REASON_INDETERMINATE = "gui.neoecoae.pattern_upload.reason.indeterminate";

    private PatternTransfer() {}

    public static Result move(PatternSlotAccess from, int fromSlot, PatternSlotAccess to, int toSlot,
        ItemStack expected) {
        if (from == null || to == null || expected == null) return rejected(REASON_SOURCE_CHANGED);
        if (!from.isLive() || !to.isLive()) return rejected(REASON_OFFLINE);
        ItemStack sourceBefore = copy(from.get(fromSlot));
        if (!PatternStacks.sameItem(sourceBefore, expected) || sourceBefore.stackSize < 1)
            return rejected(REASON_SOURCE_CHANGED);
        ItemStack moving = PatternStacks.copy(expected, 1);
        if (!to.accepts(toSlot, moving)) return rejected(REASON_TARGET_OCCUPIED);
        ItemStack targetBefore = copy(to.get(toSlot));
        ItemStack sourceAfter = PatternStacks.copy(sourceBefore, sourceBefore.stackSize - 1);
        ItemStack targetAfter = targetBefore == null ? moving
            : PatternStacks.copy(targetBefore, targetBefore.stackSize + 1);

        try {
            from.set(fromSlot, copy(sourceAfter));
        } catch (RuntimeException failure) {
            NeoECOAE.LOG.warn("Pattern upload source write failed", failure);
        }
        ItemStack sourceRead = from.get(fromSlot);
        if (PatternStacks.sameStack(sourceRead, sourceBefore)) return rejected(REASON_SOURCE_CHANGED);
        if (!PatternStacks.sameStack(sourceRead, sourceAfter))
            return restore(from, fromSlot, sourceBefore, REASON_SOURCE_CHANGED);

        try {
            to.set(toSlot, copy(targetAfter));
        } catch (RuntimeException failure) {
            NeoECOAE.LOG.warn("Pattern upload target write failed", failure);
        }
        ItemStack targetRead = to.get(toSlot);
        if (PatternStacks.sameStack(targetRead, targetAfter)) {
            changed(from);
            changed(to);
            return new Result(Status.SUCCESS, null);
        }
        if (PatternStacks.sameStack(targetRead, targetBefore))
            return restore(from, fromSlot, sourceBefore, REASON_TARGET_REJECTED);
        // The target holds something else than either known state; ownership of the pattern is unknown.
        NeoECOAE.LOG.error(
            "Pattern upload left an unexpected target state; source={} target={} expected={}",
            sourceRead,
            targetRead,
            targetAfter);
        changed(from);
        changed(to);
        return new Result(Status.INDETERMINATE, REASON_INDETERMINATE);
    }

    private static Result restore(PatternSlotAccess from, int slot, ItemStack before, String reason) {
        try {
            from.set(slot, copy(before));
        } catch (RuntimeException failure) {
            NeoECOAE.LOG.warn("Pattern upload source restore failed", failure);
        }
        if (PatternStacks.sameStack(from.get(slot), before)) return rejected(reason);
        NeoECOAE.LOG.error("Pattern upload could not restore its source slot; expected={}", before);
        changed(from);
        return new Result(Status.INDETERMINATE, REASON_INDETERMINATE);
    }

    private static void changed(PatternSlotAccess access) {
        try {
            access.changed();
        } catch (RuntimeException failure) {
            NeoECOAE.LOG.warn("Pattern upload change callback failed", failure);
        }
    }

    public static Result reject(String reason) {
        return new Result(Status.REJECTED, reason);
    }

    private static Result rejected(String reason) {
        return reject(reason);
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }
}
