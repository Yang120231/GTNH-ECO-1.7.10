package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** The source blast furnace type stays distinct from the 200-tick vanilla furnace. */
public final class BlastingRecipes {

    private static final Map<ResourceLocation, Recipe> RECIPES = new LinkedHashMap<>();

    private BlastingRecipes() {}

    static void install(ResourceLocation id, JsonObject definition) {
        var recipe = new Recipe(
            id,
            SourceIngredient.of(definition.get("ingredient")),
            SourceRecipes.output(definition),
            definition.get("experience")
                .getAsFloat(),
            definition.get("cookingtime")
                .getAsInt());
        if (RECIPES.putIfAbsent(id, recipe) != null) throw new IllegalStateException("Duplicate blasting recipe " + id);
    }

    public static Map<ResourceLocation, Recipe> getAll() {
        return Collections.unmodifiableMap(RECIPES);
    }

    public static Recipe getById(ResourceLocation id) {
        return RECIPES.get(id);
    }

    public static Recipe find(ItemStack input) {
        for (Recipe recipe : RECIPES.values()) if (recipe.ingredient()
            .test(input)) return recipe;
        return null;
    }

    @com.github.bsideup.jabel.Desugar
    public record Recipe(ResourceLocation id, SourceIngredient ingredient, ItemStack result, float experience,
        int cookingTime) {

        public Recipe {
            java.util.Objects.requireNonNull(id);
            java.util.Objects.requireNonNull(ingredient);
            if (result == null || result.stackSize <= 0 || cookingTime <= 0 || experience < 0)
                throw new IllegalArgumentException("Invalid native blasting contract");
            result = result.copy();
        }

        public ItemStack result() {
            return result.copy();
        }
    }
}
