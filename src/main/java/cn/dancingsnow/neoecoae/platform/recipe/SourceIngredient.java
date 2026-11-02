package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.JsonElement;

/** Exact item alternatives from a source ingredient, including live OreDictionary tag counterparts. */
public final class SourceIngredient {

    private final List<ItemStack> examples;
    private final Predicate<ItemStack> matcher;

    private SourceIngredient(List<ItemStack> examples) {
        this(examples, null);
    }

    private SourceIngredient(List<ItemStack> examples, Predicate<ItemStack> matcher) {
        this.examples = examples;
        this.matcher = matcher;
    }

    /** Keeps an external ingredient's matching rules separate from its NEI examples. */
    public static SourceIngredient matching(List<ItemStack> examples, Predicate<ItemStack> matcher) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : examples) copies.add(stack.copy());
        return new SourceIngredient(copies, java.util.Objects.requireNonNull(matcher));
    }

    public static SourceIngredient alternatives(ItemStack... stacks) {
        return new SourceIngredient(new ArrayList<>(java.util.Arrays.asList(stacks)));
    }

    public static SourceIngredient items(net.minecraft.item.Item... items) {
        ItemStack[] stacks = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) stacks[i] = new ItemStack(items[i]);
        return alternatives(stacks);
    }

    public static SourceIngredient of(JsonElement json) {
        var examples = new ArrayList<ItemStack>();
        collect(json, examples);
        if (examples.isEmpty()) throw new SourceRecipes.Unavailable("ingredient " + json);
        return new SourceIngredient(examples);
    }

    private static void collect(JsonElement json, List<ItemStack> examples) {
        if (json.isJsonArray()) {
            for (var entry : json.getAsJsonArray()) collect(entry, examples);
            return;
        }
        var definition = json.getAsJsonObject();
        if (definition.has("item")) {
            var stack = SourceItems.stack(
                definition.get("item")
                    .getAsString(),
                1);
            if (stack != null) examples.add(stack);
        } else if (definition.has("tag")) examples.addAll(
            SourceTags.items(
                definition.get("tag")
                    .getAsString()));
        else throw new IllegalArgumentException("Unknown source ingredient: " + json);
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return false;
        if (matcher != null) return matcher.test(stack);
        for (var candidate : examples)
            if (candidate.getItem() == stack.getItem() && (candidate.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || candidate.getItemDamage() == stack.getItemDamage()
                || stack.isItemStackDamageable())) return true;
        return false;
    }

    public boolean test(ItemStack stack) {
        return matches(stack);
    }

    public List<ItemStack> examples() {
        var result = new ArrayList<ItemStack>();
        for (var stack : examples) result.add(stack.copy());
        return result;
    }
}
