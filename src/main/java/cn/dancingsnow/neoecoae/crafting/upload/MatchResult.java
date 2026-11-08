package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

/** One decision per target, shared by the list, the auto-upload hint and the final submit check. */
public final class MatchResult {

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
        ROUTED("routed");

        public final String key;

        Level(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "gui.neoecoae.pattern_upload.match." + key;
        }

        public int rank() {
            return this == ROUTED ? 0 : ordinal();
        }
    }

    public final Level level;
    public final String reason;
    public final ItemStack circuit;
    public final String routeName;
    public final ItemStack routeIcon;
    /** Recipe map and circuit of the selected route; different signatures among auto candidates are ambiguous. */
    public final String routeSignature;
    public final boolean programmingCover;
    /** Language-independent category and requested selector used by automatic route bindings. */
    public final String categoryId;
    public final String selectionKey;

    public MatchResult(Level level, String reason, ItemStack circuit, String routeName, ItemStack routeIcon,
        String routeSignature, boolean programmingCover) {
        this(level, reason, circuit, routeName, routeIcon, routeSignature, programmingCover, "", "");
    }

    public MatchResult(Level level, String reason, ItemStack circuit, String routeName, ItemStack routeIcon,
        String routeSignature, boolean programmingCover, String categoryId, String selectionKey) {
        this.level = level;
        this.reason = reason;
        this.circuit = circuit == null ? null : circuit.copy();
        this.routeName = routeName;
        this.routeIcon = routeIcon == null ? null : routeIcon.copy();
        this.routeSignature = routeSignature == null ? "" : routeSignature;
        this.programmingCover = programmingCover;
        this.categoryId = categoryId == null ? "" : categoryId;
        this.selectionKey = selectionKey == null ? "" : selectionKey;
    }

    public static MatchResult of(Level level) {
        return new MatchResult(level, null, null, null, null, null, false);
    }

    public static MatchResult of(Level level, String reason) {
        return new MatchResult(level, reason, null, null, null, null, false);
    }

    public boolean allowsAuto() {
        return level == Level.EXACT || level == Level.ROUTED;
    }

    public boolean allowsManual() {
        return level != Level.INCOMPATIBLE;
    }

    /** A binding can declare an unknown source selector, but cannot override a known target conflict. */
    public static boolean allowsMapping(Level level, String reason) {
        return level == Level.EXACT || level == Level.ROUTED
            || level == Level.ACCEPTED
            || level == Level.UNVERIFIED && (reason == null || reason.isEmpty()
                || "gui.neoecoae.pattern_upload.reason.no_route".equals(reason)
                || "gui.neoecoae.pattern_upload.reason.circuit_unknown".equals(reason));
    }

    public boolean allowsMapping() {
        return allowsMapping(level, reason);
    }
}
