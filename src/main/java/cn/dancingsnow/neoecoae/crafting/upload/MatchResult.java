package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

/** One decision per target, shared by the list, the auto-upload hint and the final submit check. */
public final class MatchResult {

    private static final long SLOT_RANGE = 1L + Integer.MAX_VALUE;

    public enum Level {

        /** A native crafting pattern can be handled by this target. */
        EXACT("exact"),
        /** The target accepts this kind of pattern; no recipe route needs proving. */
        ACCEPTED("accepted"),
        /** The target could hold the pattern but its recipe route cannot be proven. */
        UNVERIFIED("unverified"),
        CIRCUIT_CONFLICT("circuit_conflict"),
        RATIO_MISMATCH("ratio_mismatch"),
        WRONG_RECIPE("wrong_recipe"),
        /** The target cannot hold this pattern kind at all. */
        INCOMPATIBLE("incompatible"),
        /** The recipe category and selection parameters identify this processing route. */
        ROUTED("routed"),
        GT_NATIVE("gt_native"),
        GTNR_CROSS_RECIPE("gtnr_cross_recipe");

        public final String key;

        Level(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "gui.neoecoae.pattern_upload.match." + key;
        }

        public int rank() {
            return allowsAuto() ? 0 : ordinal();
        }

        public boolean allowsAuto() {
            return this == EXACT || this == ROUTED || this == GT_NATIVE || this == GTNR_CROSS_RECIPE;
        }
    }

    public final Level level;
    public final String reason;
    /** The recipe's selected circuit, independent of the receiver's current configuration. */
    public final ItemStack circuit;
    /** A fixed receiver circuit; programming hatches and covers have no stable circuit to remember. */
    public final ItemStack configuredCircuit;
    public final String routeName;
    public final ItemStack routeIcon;
    /** GT-formatted supply voltage tier of the selected receiver; empty when it cannot be read. */
    public final String voltageTier;
    /** Numeric GT supply tier, or -1 when it cannot be read. Independent of the translated display name. */
    public final int voltageTierRank;
    /** Concrete route identity used by saved bindings and final upload revalidation. */
    public final String routeSignature;
    /** The receiver can program selectors through a hatch or cover. */
    public final boolean programmingCover;
    /** Language-independent category and requested selector used by automatic route bindings. */
    public final String categoryId;
    public final String selectionKey;
    public final String configuredRecipeMap;

    public MatchResult(Level level, String reason, ItemStack circuit, String routeName, ItemStack routeIcon,
        String routeSignature, boolean programmingCover) {
        this(level, reason, circuit, routeName, routeIcon, routeSignature, programmingCover, "", "");
    }

    public MatchResult(Level level, String reason, ItemStack circuit, String routeName, ItemStack routeIcon,
        String routeSignature, boolean programmingCover, String categoryId, String selectionKey) {
        this(
            level,
            reason,
            circuit,
            routeName,
            routeIcon,
            routeSignature,
            programmingCover,
            categoryId,
            selectionKey,
            "");
    }

    public MatchResult(Level level, String reason, ItemStack circuit, String routeName, ItemStack routeIcon,
        String routeSignature, boolean programmingCover, String categoryId, String selectionKey, String voltageTier) {
        this(
            level,
            reason,
            circuit,
            routeName,
            routeIcon,
            routeSignature,
            programmingCover,
            categoryId,
            selectionKey,
            voltageTier,
            null,
            -1);
    }

    public MatchResult(Level level, String reason, ItemStack circuit, String routeName, ItemStack routeIcon,
        String routeSignature, boolean programmingCover, String categoryId, String selectionKey, String voltageTier,
        ItemStack configuredCircuit, int voltageTierRank) {
        this(
            level,
            reason,
            circuit,
            routeName,
            routeIcon,
            routeSignature,
            programmingCover,
            categoryId,
            selectionKey,
            voltageTier,
            configuredCircuit,
            voltageTierRank,
            "");
    }

    public MatchResult(Level level, String reason, ItemStack circuit, String routeName, ItemStack routeIcon,
        String routeSignature, boolean programmingCover, String categoryId, String selectionKey, String voltageTier,
        ItemStack configuredCircuit, int voltageTierRank, String configuredRecipeMap) {
        this.level = level;
        this.reason = reason;
        this.circuit = circuit == null ? null : circuit.copy();
        this.configuredCircuit = programmingCover || configuredCircuit == null ? null : configuredCircuit.copy();
        this.routeName = routeName;
        this.routeIcon = routeIcon == null ? null : routeIcon.copy();
        this.voltageTier = voltageTier == null ? "" : voltageTier;
        this.voltageTierRank = voltageTierRank;
        this.routeSignature = routeSignature == null ? "" : routeSignature;
        this.programmingCover = programmingCover;
        this.categoryId = categoryId == null ? "" : categoryId;
        this.selectionKey = selectionKey == null ? "" : selectionKey;
        this.configuredRecipeMap = configuredRecipeMap;
    }

    /**
     * Higher scores win: GT voltage tier, free slots, then programming or a matching fixed circuit. Separate ranges
     * ensure even Integer.MAX_VALUE free slots cannot outweigh one voltage tier; GT tiers range from 0 to 15.
     */
    public long priorityScore(int emptySlots) {
        long voltage = Math.max(-1, Math.min(15, voltageTierRank)) + 1L;
        int circuit = programmingCover ? 2
            : configuredCircuit != null && this.circuit != null && configuredCircuit.isItemEqual(this.circuit) ? 1 : 0;
        return (voltage * SLOT_RANGE + Math.max(0, emptySlots)) * 3 + circuit;
    }

    /** Different GT receiver implementations can execute the same proven recipe map and circuit. */
    public boolean sameRecipeSelection(MatchResult other) {
        if (!categoryId.equals(other.categoryId)) return false;
        if (!categoryId.startsWith("gt:")) return routeSignature.equals(other.routeSignature);
        return circuit == null ? other.circuit == null : other.circuit != null && circuit.isItemEqual(other.circuit);
    }

    public static MatchResult of(Level level) {
        return new MatchResult(level, null, null, null, null, null, false);
    }

    public static MatchResult of(Level level, String reason) {
        return new MatchResult(level, reason, null, null, null, null, false);
    }

    public boolean allowsAuto() {
        return level.allowsAuto();
    }

    public boolean allowsManual() {
        return level != Level.INCOMPATIBLE;
    }

    /** A binding can declare an unknown source selector, but cannot override a known target conflict. */
    public static boolean allowsMapping(Level level, String reason) {
        return level.allowsAuto() || level == Level.ACCEPTED
            || level == Level.UNVERIFIED && (reason == null || reason.isEmpty()
                || "gui.neoecoae.pattern_upload.reason.no_route".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.no_machine".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.no_source".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.cross_gt".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.nei_recipe_changed".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.no_machines".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.machine_mismatch".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.circuit_unknown".equals(reason));
    }

    public boolean allowsMapping() {
        return allowsMapping(level, reason);
    }
}
