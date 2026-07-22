package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

import gregtech.common.items.ItemIntegratedCircuit;

/** A short-lived route hint captured while a recipe is transferred into a pattern terminal. */
public final class PatternRouteKey {

    private final String recipeMapId;
    private final ItemStack circuit;
    private final int circuitNumber;

    public PatternRouteKey(String recipeMapId) {
        this(recipeMapId, null);
    }

    public PatternRouteKey(String recipeMapId, ItemStack circuit) {
        this.recipeMapId = recipeMapId == null ? "" : recipeMapId.trim();
        ItemStack normalized = PatternCircuitCompat.unwrap(circuit);
        this.circuit = validCircuit(circuit) ? (normalized == null ? circuit.copy() : normalized) : null;
        this.circuitNumber = this.circuit != null && this.circuit.getItem() instanceof ItemIntegratedCircuit
            ? this.circuit.getItemDamage() & 0xFF
            : -1;
    }

    public String getRecipeMapId() {
        return this.recipeMapId;
    }

    public ItemStack getCircuit() {
        return this.circuit == null ? null : this.circuit.copy();
    }

    public int getCircuitNumber() {
        return this.circuitNumber;
    }

    public boolean hasCircuit() {
        return this.circuit != null;
    }

    public boolean matchesCircuit(ItemStack candidate) {
        return validCircuit(candidate) && this.hasCircuit() && PatternCircuitCompat.same(this.circuit, candidate);
    }

    public boolean isEmpty() {
        return this.recipeMapId.isEmpty();
    }

    public boolean matches(String candidate) {
        return !this.isEmpty() && candidate != null && this.recipeMapId.equals(candidate);
    }

    private static boolean validCircuit(ItemStack stack) {
        if (!PatternCircuitCompat.isVirtualCircuit(stack)) return false;
        ItemStack normalized = PatternCircuitCompat.unwrap(stack);
        if (normalized == null || !(normalized.getItem() instanceof ItemIntegratedCircuit)) return true;
        return validCircuitNumber(normalized.getItemDamage());
    }

    private static boolean validCircuitNumber(int number) {
        return number >= 0 && number <= ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER;
    }
}
