package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

/** One decision per target, shared by the list, the auto-upload hint and the final submit check. */
public final class MatchResult {

    public enum Level {

        /** Recipe, quantities and circuit were verified against the target's actual configuration. */
        EXACT("exact"),
        /** The target accepts this kind of pattern; no recipe route needs proving. */
        ACCEPTED("accepted"),
        /** The target could hold the pattern but its recipe route cannot be proven. */
        UNVERIFIED("unverified"),
        CIRCUIT_CONFLICT("circuit_conflict"),
        RATIO_MISMATCH("ratio_mismatch"),
        WRONG_RECIPE("wrong_recipe"),
        /** The target cannot hold this pattern kind at all. */
        INCOMPATIBLE("incompatible");

        public final String key;

        Level(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "gui.neoecoae.pattern_upload.match." + key;
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

    public MatchResult(Level level, String reason, ItemStack circuit, String routeName, ItemStack routeIcon,
        String routeSignature, boolean programmingCover) {
        this.level = level;
        this.reason = reason;
        this.circuit = circuit == null ? null : circuit.copy();
        this.routeName = routeName;
        this.routeIcon = routeIcon == null ? null : routeIcon.copy();
        this.routeSignature = routeSignature == null ? "" : routeSignature;
        this.programmingCover = programmingCover;
    }

    public static MatchResult of(Level level) {
        return new MatchResult(level, null, null, null, null, null, false);
    }

    public static MatchResult of(Level level, String reason) {
        return new MatchResult(level, reason, null, null, null, null, false);
    }

    public boolean allowsAuto() {
        return level == Level.EXACT;
    }

    public boolean allowsManual() {
        return level != Level.INCOMPATIBLE;
    }
}
