package cn.dancingsnow.neoecoae.crafting.cooling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.all.NEFluids;

public final class ECOCoolingRecipes {

    private static final List<ECOCoolingRecipe> RECIPES = new ArrayList<ECOCoolingRecipe>();

    private ECOCoolingRecipes() {}

    public static void registerDefaults() {
        RECIPES.clear();
        register(FluidRegistry.WATER, 100, null, 1500, 2);
        register(NEFluids.cryotheumSolution(), 100, null, 12000, 9);
    }

    public static void register(Fluid input, int inputAmount, FluidStack output, int coolant, int maxOverclock) {
        if (input == null || inputAmount <= 0 || coolant <= 0) {
            return;
        }
        RECIPES.add(new ECOCoolingRecipe(input, inputAmount, output, coolant, maxOverclock));
    }

    public static ECOCoolingRecipe find(FluidStack input, FluidStack output) {
        for (ECOCoolingRecipe recipe : RECIPES) {
            if (recipe.matches(input, output)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<ECOCoolingRecipe> all() {
        return Collections.unmodifiableList(RECIPES);
    }
}
