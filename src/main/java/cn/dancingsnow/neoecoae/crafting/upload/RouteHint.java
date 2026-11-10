package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * Recipe route selected while NEI fills a pattern terminal. The editor keeps it while that draft is edited and
 * re-encoded; a separate copy is bound to each encoded result. It never authorizes a network write.
 *
 * <p>
 * Non-GT categories use NEI's registered machine catalysts. Display names and handler icons are not route evidence.
 * </p>
 */
public final class RouteHint {

    public static final String MATERIAL_ORDER_CATEGORY = "neoecoae:multiblock_materials";

    private static final int MAX_LENGTH = 128;

    private final String recipeMapId;
    private ItemStack circuit;
    private final String category;
    private final String categoryId;
    private final List<ItemStack> machineTypes;
    private final int windowId;
    private ItemStack boundPattern;

    public RouteHint(String recipeMapId, ItemStack circuit, int windowId) {
        this(recipeMapId, circuit, "", windowId);
    }

    public RouteHint(String recipeMapId, ItemStack circuit, String category, int windowId) {
        this(
            recipeMapId,
            circuit,
            category,
            recipeMapId == null || recipeMapId.trim()
                .isEmpty() ? "" : "gt:" + recipeMapId.trim(),
            windowId);
    }

    public RouteHint(String recipeMapId, ItemStack circuit, String category, String categoryId, int windowId) {
        this(recipeMapId, circuit, category, categoryId, windowId, Collections.emptyList());
    }

    public RouteHint(String recipeMapId, ItemStack circuit, String category, String categoryId, int windowId,
        List<ItemStack> machineTypes) {
        this.recipeMapId = clip(recipeMapId);
        this.circuit = circuit == null ? null : PatternStacks.copy(circuit, 1);
        this.category = clip(category);
        // Identifiers must never be truncated into another category's identifier.
        String id = categoryId == null ? "" : categoryId.trim();
        this.categoryId = id.length() <= 512 ? id : "";
        this.windowId = windowId;
        List<ItemStack> types = new ArrayList<>();
        if (machineTypes != null && machineTypes.size() <= 128) for (ItemStack type : machineTypes)
            if (type != null && type.getItem() != null) types.add(PatternStacks.copy(type, 1));
        this.machineTypes = Collections.unmodifiableList(types);
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

    /** Only an explicit selection circuit in a successfully encoded draft changes the NEI selection. */
    public void selectCircuit(ItemStack selected) {
        circuit = selected == null ? null : PatternStacks.copy(selected, 1);
    }

    public RouteHint copy() {
        return new RouteHint(recipeMapId, circuit, category, categoryId, windowId, machineTypes);
    }

    public boolean matchesMachine(ItemStack machine) {
        if (machine == null || machine.getItem() == null) return false;
        for (ItemStack type : machineTypes) if (type.getItem() == machine.getItem()
            && (type.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                || type.getItemDamage() == machine.getItemDamage())
            && (!type.hasTagCompound() || ItemStack.areItemStackTagsEqual(type, machine))) return true;
        return false;
    }

    public boolean hasMachineTypes() {
        return !machineTypes.isEmpty();
    }

    /** A missing circuit in a known NEI recipe is different from an unknown selector on an old pattern. */
    public String selectionKey() {
        return circuit == null ? "-" : Integer.toString(circuit.getItemDamage() & 0xFF);
    }

    /** Display name of the NEI recipe category; empty when unknown. */
    public String getCategory() {
        return category;
    }

    /** Language-independent NEI category identity, used only for player-defined upload mappings. */
    public String getCategoryId() {
        return categoryId;
    }

    /** True when the hint names a recipe map and may therefore take part in route matching. */
    public boolean hasRecipeMap() {
        return !recipeMapId.isEmpty();
    }

    public boolean isMaterialOrder() {
        return MATERIAL_ORDER_CATEGORY.equals(categoryId);
    }

    public boolean isEmpty() {
        return recipeMapId.isEmpty() && category.isEmpty() && categoryId.isEmpty();
    }

    public boolean isPending() {
        return boundPattern == null;
    }

    /** Binds this copy to one successful encoding; the editor's source is kept separately. */
    public void bind(ItemStack pattern) {
        boundPattern = PatternStacks.copy(pattern, 1);
    }

    public boolean appliesTo(int currentWindowId, ItemStack pattern) {
        return !isEmpty() && windowId == currentWindowId
            && boundPattern != null
            && PatternStacks.sameItem(boundPattern, pattern);
    }

    public boolean expired(int currentWindowId) {
        return windowId != currentWindowId;
    }
}
