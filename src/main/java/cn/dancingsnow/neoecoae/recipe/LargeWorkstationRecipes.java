package cn.dancingsnow.neoecoae.recipe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes;

/** Source core pattern matching and cost ordering over the actual native workstation catalog. */
public final class LargeWorkstationRecipes {

    private static List<LargeWorkstationRecipe> cache = Collections.emptyList();
    private static int cachedCount = -1;

    private LargeWorkstationRecipes() {}

    public static synchronized List<LargeWorkstationRecipe> getAll(World level) {
        var nativeRecipes = WorkstationRecipes.getAll();
        if (cachedCount != nativeRecipes.size()) {
            List<LargeWorkstationRecipe> result = new ArrayList<>();
            for (var entry : nativeRecipes.entrySet()) result.add(
                new LargeWorkstationRecipe(
                    entry.getKey(),
                    entry.getValue(),
                    entry.getValue()
                        .energy(),
                    Collections.emptyList()));
            cache = Collections.unmodifiableList(result);
            cachedCount = nativeRecipes.size();
        }
        return cache;
    }

    public static LargeWorkstationRecipe getById(World level, ResourceLocation id) {
        for (var recipe : getAll(level)) if (recipe.id()
            .equals(id)) return recipe;
        return null;
    }

    public static LargeWorkstationRecipe find(World level, KeyCounter inputs, KeyCounter outputs) {
        return find(getAll(level), inputs, outputs);
    }

    public static PatternMatch findScaled(World level, KeyCounter inputs, KeyCounter outputs) {
        return findScaled(getAll(level), inputs, outputs);
    }

    private static final Comparator<LargeWorkstationRecipe> COST_ORDER = (first, second) -> {
        int costs = compareCosts(first, 1, second, 1);
        return costs != 0 ? costs
            : first.id()
                .toString()
                .compareTo(
                    second.id()
                        .toString());
    };

    private static int compareCosts(LargeWorkstationRecipe first, long firstMultiplier, LargeWorkstationRecipe second,
        long secondMultiplier) {
        int lightning = Boolean.compare(
            first.extraInputs()
                .stream()
                .noneMatch(LargeWorkstationRecipes::isLightning),
            second.extraInputs()
                .stream()
                .noneMatch(LargeWorkstationRecipes::isLightning));
        if (lightning != 0) return lightning;
        // Different currencies have no universal price ordering. Keep a deterministic contract order.
        int currency = Integer.compare(energyKind(first), energyKind(second));
        if (currency != 0) return currency;
        int energy = BigInteger.valueOf(first.energy())
            .multiply(BigInteger.valueOf(firstMultiplier))
            .compareTo(
                BigInteger.valueOf(second.energy())
                    .multiply(BigInteger.valueOf(secondMultiplier)));
        if (energy != 0) return energy;
        int fe = BigInteger.valueOf(first.feEnergy())
            .multiply(BigInteger.valueOf(firstMultiplier))
            .compareTo(
                BigInteger.valueOf(second.feEnergy())
                    .multiply(BigInteger.valueOf(secondMultiplier)));
        if (fe != 0) return fe;
        int extras = extraCost(first).multiply(BigInteger.valueOf(firstMultiplier))
            .compareTo(extraCost(second).multiply(BigInteger.valueOf(secondMultiplier)));
        if (extras != 0) return extras;
        return BigInteger.valueOf(first.processingTicks())
            .multiply(BigInteger.valueOf(firstMultiplier))
            .compareTo(
                BigInteger.valueOf(second.processingTicks())
                    .multiply(BigInteger.valueOf(secondMultiplier)));
    }

    private static BigInteger extraCost(LargeWorkstationRecipe recipe) {
        return recipe.extraInputs()
            .stream()
            .map(input -> BigInteger.valueOf(input.amount()))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static int energyKind(LargeWorkstationRecipe recipe) {
        return recipe.feEnergy() == 0 ? 0 : recipe.energy() == 0 ? 1 : recipe.feAlternative() ? 2 : 3;
    }

    private static boolean isLightning(GenericStack stack) {
        return stack.what()
            .getType()
            .getId()
            .getNamespace()
            .equals("ae2lt");
    }

    private static LargeWorkstationRecipe find(List<LargeWorkstationRecipe> recipes, KeyCounter inputs,
        KeyCounter outputs) {
        LargeWorkstationRecipe best = null;
        for (var recipe : recipes) {
            if (!recipe.matches(inputs, outputs)) continue;
            if (best == null || COST_ORDER.compare(recipe, best) < 0) best = recipe;
        }
        return best;
    }

    @Nullable
    static PatternMatch findScaled(List<LargeWorkstationRecipe> recipes, KeyCounter inputs, KeyCounter outputs) {
        PatternMatch best = null;
        for (var recipe : recipes) {
            long multiplier = recipe.matchingMultiplier(inputs, outputs);
            if (multiplier == 0) continue;
            try {
                Math.multiplyExact(recipe.energy(), multiplier);
                Math.multiplyExact(recipe.feEnergy(), multiplier);
                Math.toIntExact(Math.multiplyExact((long) recipe.processingTicks(), multiplier));
                for (var extra : recipe.extraInputs()) Math.multiplyExact(extra.amount(), multiplier);
            } catch (ArithmeticException overflow) {
                // An unpayable preferred route must not hide another legal variant of this pattern.
                continue;
            }
            var match = new PatternMatch(recipe, multiplier);
            if (best == null) best = match;
            else {
                int costs = compareCosts(recipe, multiplier, best.recipe(), best.multiplier());
                if (costs < 0 || costs == 0 && recipe.id()
                    .toString()
                    .compareTo(
                        best.recipe()
                            .id()
                            .toString())
                    < 0) best = match;
            }
        }
        return best;
    }

    @com.github.bsideup.jabel.Desugar
    public record PatternMatch(LargeWorkstationRecipe recipe, long multiplier) {}
}
