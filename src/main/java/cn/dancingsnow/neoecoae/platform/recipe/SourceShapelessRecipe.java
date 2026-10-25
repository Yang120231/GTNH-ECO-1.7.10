package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/** Source alternative matching and smithing data inheritance on the native crafting grid. */
public final class SourceShapelessRecipe extends ShapelessOreRecipe {

    private final List<SourceIngredient> ingredients;
    private final boolean smithing;

    public SourceShapelessRecipe(ItemStack output, List<SourceIngredient> ingredients, boolean smithing) {
        super(
            output,
            ingredients.stream()
                .map(
                    ingredient -> ingredient.examples()
                        .get(0))
                .toArray());
        for (int i = 0; i < ingredients.size(); i++) getInput().set(
            i,
            ingredients.get(i)
                .examples());
        this.ingredients = new ArrayList<>(ingredients);
        this.smithing = smithing;
    }

    private int[] assignment(InventoryCrafting table) {
        var slots = new ArrayList<Integer>();
        for (int slot = 0; slot < table.getSizeInventory(); slot++)
            if (table.getStackInSlot(slot) != null) slots.add(slot);
        if (slots.size() != ingredients.size()) return null;
        int[] assignment = new int[ingredients.size()];
        return assign(table, slots, new boolean[slots.size()], assignment, 0) ? assignment : null;
    }

    private boolean assign(InventoryCrafting table, List<Integer> slots, boolean[] used, int[] assignment,
        int ingredient) {
        if (ingredient == ingredients.size()) return true;
        for (int i = 0; i < slots.size(); i++) if (!used[i] && ingredients.get(ingredient)
            .matches(table.getStackInSlot(slots.get(i)))) {
                used[i] = true;
                assignment[ingredient] = slots.get(i);
                if (assign(table, slots, used, assignment, ingredient + 1)) return true;
                used[i] = false;
            }
        return false;
    }

    @Override
    public boolean matches(InventoryCrafting table, World world) {
        return assignment(table) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting table) {
        int[] slots = assignment(table);
        if (slots == null) return null;
        ItemStack result = getRecipeOutput().copy();
        if (smithing) {
            ItemStack base = table.getStackInSlot(slots[1]);
            if (base.hasTagCompound()) result.setTagCompound(
                (net.minecraft.nbt.NBTTagCompound) base.getTagCompound()
                    .copy());
            result.setItemDamage(base.getItemDamage());
        }
        return result;
    }
}
