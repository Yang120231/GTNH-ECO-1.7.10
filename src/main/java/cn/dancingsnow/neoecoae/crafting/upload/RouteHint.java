package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

/**
 * Recipe source captured while NEI fills a pattern terminal. It is only advisory: it belongs to one terminal window,
 * is bound to the pattern encoded right after it, and expires. It never authorizes a network write.
 */
public final class RouteHint {

    private static final long LIFETIME_MILLIS = 10L * 60L * 1000L;

    private final String recipeMapId;
    private final ItemStack circuit;
    private final int windowId;
    private final long createdAt;
    private ItemStack boundPattern;

    public RouteHint(String recipeMapId, ItemStack circuit, int windowId) {
        String id = recipeMapId == null ? "" : recipeMapId.trim();
        this.recipeMapId = id.length() > 128 ? id.substring(0, 128) : id;
        this.circuit = circuit == null ? null : PatternStacks.copy(circuit, 1);
        this.windowId = windowId;
        this.createdAt = System.currentTimeMillis();
    }

    public String getRecipeMapId() {
        return recipeMapId;
    }

    public ItemStack getCircuit() {
        return circuit == null ? null : circuit.copy();
    }

    public boolean isEmpty() {
        return recipeMapId.isEmpty();
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
