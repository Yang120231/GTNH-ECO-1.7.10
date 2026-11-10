package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
        public final String positionKey;
        public final Object storageIdentity;
        public final MatchResult match;
        public final SlotState state;
        public final int emptySlots;
        public final int totalSlots;
        public final int freeSlot;

        Evaluation(PatternUploadTarget target, MatchResult match, SlotState state, int emptySlots, int totalSlots,
            int freeSlot) {
            this.target = target;
            this.positionKey = target.positionKey();
            Object identity = null;
            try {
                identity = target.storageIdentity();
            } catch (RuntimeException unavailable) { /* The position still identifies this receiver. */ }
            this.storageIdentity = identity;
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

        public boolean canUploadByMapping(PatternUploadMappings.Mapping mapping) {
            return state == SlotState.FREE && mapping != null
                && mapping.enabled
                && match.allowsMapping()
                && mapping.matches(this);
        }
    }

    public static final class AutoChoice {

        public final Evaluation selected;
        /** Rejection reason, or a warning when an unverified receiver was selected. */
        public final String reason;
        /** A player binding chose the concrete receiver. */
        public final boolean mapped;

        AutoChoice(Evaluation selected, String reason) {
            this(selected, reason, false);
        }

        AutoChoice(Evaluation selected, String reason, boolean mapped) {
            this.selected = selected;
            this.reason = reason;
            this.mapped = mapped;
        }
    }

    public static final Comparator<Evaluation> ORDER = Comparator
        .<Evaluation>comparingInt(evaluation -> evaluation.state == SlotState.OFFLINE ? 1 : 0)
        .thenComparingInt(PatternTargetSelector::craftingPriority)
        .thenComparingInt(evaluation -> evaluation.match.level.rank())
        .thenComparingInt(evaluation -> evaluation.state.ordinal())
        .thenComparingInt(evaluation -> evaluation.target.kind().priority)
        .thenComparingInt(evaluation -> -evaluation.emptySlots)
        .thenComparing(evaluation -> evaluation.positionKey);

    private PatternTargetSelector() {}

    /** Crafting destinations: ECO F domain, GTNL matrix, then molecular assemblers. */
    private static int craftingPriority(Evaluation evaluation) {
        if (!"ae2:crafting".equals(evaluation.match.categoryId)) return 3;
        return switch (evaluation.target.kind()) {
            case ECO_PATTERN_DOMAIN -> 0;
            case GTNL_ASSEMBLER_MATRIX -> 1;
            case CRAFTING_ASSEMBLER, AE2_INTERFACE, AE2_DUAL_INTERFACE -> 2;
            default -> 3;
        };
    }

    public static Evaluation evaluate(PatternUploadTarget target, PatternContext context, IGrid grid) {
        try {
            MatchResult match = target.match(context);
            if (!target.isPresent(grid)) return new Evaluation(target, match, SlotState.OFFLINE, 0, 0, -1);
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
        List<Evaluation> routes = new ArrayList<>();
        boolean unknownSelector = false;
        for (Evaluation evaluation : sorted) {
            String reason = evaluation.match.reason;
            if ("gui.neoecoae.pattern_upload.reason.search_incomplete".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.route_ambiguous".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.tunnel_missing".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.tunnel_invalid".equals(reason))
                return new AutoChoice(null, reason);
            if (evaluation.match.allowsAuto()
                || evaluation.match.level == MatchResult.Level.UNVERIFIED && evaluation.match.allowsMapping()
                    && !evaluation.match.categoryId.isEmpty()
                    && !evaluation.match.routeSignature.isEmpty())
                routes.add(evaluation);
            else if ("gui.neoecoae.pattern_upload.reason.circuit_unknown".equals(reason)) unknownSelector = true;
        }
        if (unknownSelector) return new AutoChoice(
            null,
            routes.isEmpty() ? "gui.neoecoae.pattern_upload.reason.circuit_unknown"
                : "gui.neoecoae.pattern_upload.reason.route_ambiguous");
        if (routes.isEmpty()) {
            for (Evaluation evaluation : sorted)
                if (evaluation.match.reason != null && evaluation.match.level != MatchResult.Level.WRONG_RECIPE
                    && evaluation.match.level != MatchResult.Level.INCOMPATIBLE)
                    return new AutoChoice(null, evaluation.match.reason);
            for (Evaluation evaluation : sorted)
                if ("gui.neoecoae.pattern_upload.reason.fluid_input".equals(evaluation.match.reason)
                    || "gui.neoecoae.pattern_upload.reason.bec_role".equals(evaluation.match.reason))
                    return new AutoChoice(null, evaluation.match.reason);
            return new AutoChoice(null, "gui.neoecoae.pattern_upload.reason.no_exact");
        }
        Set<String> signatures = new HashSet<>();
        if (routes.stream()
            .allMatch(evaluation -> "ae2:crafting".equals(evaluation.match.categoryId))) {
            int priority = routes.stream()
                .mapToInt(PatternTargetSelector::craftingPriority)
                .min()
                .orElse(3);
            routes.removeIf(evaluation -> craftingPriority(evaluation) != priority);
        }
        for (Evaluation evaluation : routes) signatures.add(evaluation.match.routeSignature);
        if (signatures.size() > 1) return new AutoChoice(null, "gui.neoecoae.pattern_upload.reason.route_ambiguous");
        // Full, duplicate and offline receivers still participate in identity resolution.
        if (receiverCount(routes) != 1) return new AutoChoice(null, "gui.neoecoae.pattern_upload.reason.ambiguous");
        return available(routes.get(0), false);
    }

    /** Explicit bindings are authoritative, including a full, missing or reconfigured destination. */
    public static AutoChoice choose(List<Evaluation> sorted, PatternUploadMappings.Mapping mapping) {
        if (mapping == null || !mapping.enabled) return choose(sorted);
        if (mapping.conflict) return new AutoChoice(null, "gui.neoecoae.pattern_upload.mapping.ambiguous");
        List<Evaluation> owned = new ArrayList<>();
        for (Evaluation evaluation : sorted) if (mapping.owns(evaluation)) owned.add(evaluation);
        if (owned.isEmpty()) return new AutoChoice(null, "gui.neoecoae.pattern_upload.mapping.target_missing");
        if (receiverCount(owned) != 1)
            return new AutoChoice(null, "gui.neoecoae.pattern_upload.mapping.legacy_ambiguous");
        Evaluation target = owned.get(0);
        if (target.state == SlotState.OFFLINE) return new AutoChoice(null, target.state.translationKey());
        if (!target.match.allowsMapping()) return new AutoChoice(null, rejection(target));
        if (!mapping.matches(target)) return new AutoChoice(null, "gui.neoecoae.pattern_upload.mapping.target_changed");
        return available(target, true);
    }

    private static AutoChoice available(Evaluation target, boolean mapped) {
        return target.state == SlotState.FREE
            ? new AutoChoice(
                target,
                target.match.level == MatchResult.Level.UNVERIFIED ? "gui.neoecoae.pattern_upload.warning.unverified"
                    : null,
                mapped)
            : new AutoChoice(null, rejection(target));
    }

    static String rejection(Evaluation target) {
        if (target.match.level == MatchResult.Level.INCOMPATIBLE && target.match.reason != null)
            return target.match.reason;
        if (target.state != SlotState.FREE) return target.state.translationKey();
        return target.match.reason == null || target.match.reason.isEmpty() ? target.match.level.translationKey()
            : target.match.reason;
    }

    private static int receiverCount(List<Evaluation> evaluations) {
        Set<Object> inventories = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> positions = new HashSet<>();
        int count = 0;
        for (Evaluation evaluation : evaluations) {
            Object inventory = evaluation.storageIdentity;
            if (positions.add(evaluation.positionKey) && (inventory == null || inventories.add(inventory))) count++;
        }
        return count;
    }

    public static boolean sameReceiver(Evaluation left, Evaluation right) {
        return left.positionKey.equals(right.positionKey)
            || left.storageIdentity != null && left.storageIdentity == right.storageIdentity;
    }
}
