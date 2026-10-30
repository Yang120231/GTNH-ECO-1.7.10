package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

/**
 * Recipe source captured while NEI fills a pattern terminal. It is only advisory: it belongs to one terminal window,
 * is bound to the pattern encoded right after it, and expires. It never authorizes a network write.
 *
 * <p>
 * A hint may carry only the NEI category name (for recipes without a GregTech recipe map); it then still prefills
 * the client search but takes no part in route matching, see {@link #hasRecipeMap()}.
 * </p>
 */
public final class RouteHint {

    private static final long LIFETIME_MILLIS = 10L * 60L * 1000L;
    private static final int MAX_LENGTH = 128;

    private final String recipeMapId;
    private final ItemStack circuit;
    private final String category;
    private final int windowId;
    private final long createdAt;
    private ItemStack boundPattern;

    public RouteHint(String recipeMapId, ItemStack circuit, int windowId) {
        this(recipeMapId, circuit, "", windowId);
    }

    public RouteHint(String recipeMapId, ItemStack circuit, String category, int windowId) {
        this.recipeMapId = clip(recipeMapId);
        this.circuit = circuit == null ? null : PatternStacks.copy(circuit, 1);
        this.category = clip(category);
        this.windowId = windowId;
        this.createdAt = System.currentTimeMillis();
    }

    private static String clip(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }

    public String getRecipeMapId() {
        return recipeMapId;
    }

    public ItemStack getCircuit() {
        return circuit == null ? null : circuit.copy();
    }

    /** Display name of the NEI recipe category; empty when unknown. */
    public String getCategory() {
        return category;
    }

    /** True when the hint names a recipe map and may therefore take part in route matching. */
    public boolean hasRecipeMap() {
        return !recipeMapId.isEmpty();
    }

    public boolean isEmpty() {
        return recipeMapId.isEmpty() && category.isEmpty();
    }

    public boolean isPending() {
        return boundPattern == null;
    }

    /** Called once when the terminal encodes; a hint never follows a later manual edit. */
    public void bind(ItemStack pattern) {
        if (boundPattern == null && pattern != null) boundPattern = PatternStacks.copy(pattern, 1);
    }

    public boolean appliesTo(int currentWindowId, ItemStack pattern) {
        return !isEmpty() && windowId == currentWindowId
            && boundPattern != null
            && PatternStacks.sameItem(boundPattern, pattern)
            && System.currentTimeMillis() - createdAt < LIFETIME_MILLIS;
    }

    public boolean expired(int currentWindowId) {
        return windowId != currentWindowId || System.currentTimeMillis() - createdAt >= LIFETIME_MILLIS;
    }
}
