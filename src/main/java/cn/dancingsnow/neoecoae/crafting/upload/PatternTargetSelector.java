package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.NeoECOAE;

/** Evaluates targets once and derives list order and the auto-upload choice from the same results. */
public final class PatternTargetSelector {

    public enum SlotState {

        FREE("free"),
        DUPLICATE("duplicate"),
        FULL("full"),
        OFFLINE("offline");

        public final String key;

        SlotState(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "gui.neoecoae.pattern_upload.state." + key;
        }
    }

    public static final class Evaluation {

        public final PatternUploadTarget target;
        public final MatchResult match;
        public final SlotState state;
        public final int emptySlots;
        public final int totalSlots;
        public final int freeSlot;

        Evaluation(PatternUploadTarget target, MatchResult match, SlotState state, int emptySlots, int totalSlots,
            int freeSlot) {
            this.target = target;
            this.match = match;
            this.state = state;
            this.emptySlots = emptySlots;
            this.totalSlots = totalSlots;
            this.freeSlot = freeSlot;
        }

        public boolean canUploadManually() {
            return state == SlotState.FREE && match.allowsManual();
        }

        public boolean canUploadAutomatically() {
            return state == SlotState.FREE && match.allowsAuto();
        }
    }

    public static final class AutoChoice {

        public final Evaluation selected;
        /** Translation key explaining why nothing was selected. */
        public final String reason;

        AutoChoice(Evaluation selected, String reason) {
            this.selected = selected;
            this.reason = reason;
        }
    }

    public static final Comparator<Evaluation> ORDER = Comparator
        .<Evaluation>comparingInt(evaluation -> evaluation.state == SlotState.OFFLINE ? 1 : 0)
        .thenComparingInt(evaluation -> evaluation.match.level.ordinal())
        .thenComparingInt(evaluation -> evaluation.state.ordinal())
        .thenComparingInt(evaluation -> evaluation.target.kind().priority)
        .thenComparingInt(evaluation -> -evaluation.emptySlots)
        .thenComparing(evaluation -> evaluation.target.positionKey());

    private PatternTargetSelector() {}

    public static Evaluation evaluate(PatternUploadTarget target, PatternContext context, IGrid grid) {
        try {
            if (!target.isPresent(grid)) return new Evaluation(
                target,
                MatchResult.of(MatchResult.Level.UNVERIFIED),
                SlotState.OFFLINE,
                0,
                0,
                -1);
            MatchResult match = target.match(context);
            PatternSlotAccess slots = target.slots();
            ItemStack one = PatternStacks.copy(context.pattern, 1);
            int empty = 0, free = -1;
            boolean duplicate = false;
            for (int slot = 0; slot < slots.size(); slot++) {
                ItemStack existing = slots.get(slot);
                if (existing == null) {
                    empty++;
                    if (free < 0 && slots.accepts(slot, one)) free = slot;
                } else if (PatternStacks.samePattern(existing, one)) duplicate = true;
            }
            SlotState state = duplicate ? SlotState.DUPLICATE : free >= 0 ? SlotState.FREE : SlotState.FULL;
            return new Evaluation(target, match, state, empty, slots.size(), duplicate ? -1 : free);
        } catch (RuntimeException failure) {
            // A host can be rebuilt while it is inspected; it is shown offline and never written.
            NeoECOAE.LOG.debug("Pattern upload target inspection failed", failure);
            return new Evaluation(target, MatchResult.of(MatchResult.Level.UNVERIFIED), SlotState.OFFLINE, 0, 0, -1);
        }
    }

    public static List<Evaluation> evaluateAll(List<PatternUploadTarget> targets, PatternContext context, IGrid grid) {
        List<Evaluation> result = new ArrayList<>(targets.size());
        for (PatternUploadTarget target : targets) result.add(evaluate(target, context, grid));
        result.sort(ORDER);
        return result;
    }

    public static AutoChoice choose(List<Evaluation> sorted) {
        List<Evaluation> exact = new ArrayList<>();
        for (Evaluation evaluation : sorted) if (evaluation.canUploadAutomatically()) exact.add(evaluation);
        if (exact.isEmpty()) {
            for (Evaluation evaluation : sorted)
                if (evaluation.match.allowsAuto() && evaluation.state == SlotState.DUPLICATE)
                    return new AutoChoice(null, "gui.neoecoae.pattern_upload.reason.duplicate");
            return new AutoChoice(null, "gui.neoecoae.pattern_upload.reason.no_exact");
        }
        Set<String> signatures = new HashSet<>();
        for (Evaluation evaluation : exact) signatures.add(evaluation.match.routeSignature);
        if (signatures.size() > 1) return new AutoChoice(null, "gui.neoecoae.pattern_upload.reason.ambiguous");
        return new AutoChoice(exact.get(0), null);
    }
}
