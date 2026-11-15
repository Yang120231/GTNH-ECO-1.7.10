package cn.dancingsnow.neoecoae.compat.crafttweaker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SourceIngredient;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import minetweaker.IUndoableAction;
import minetweaker.api.item.IIngredient;
import minetweaker.api.item.IItemStack;
import minetweaker.api.liquid.ILiquidStack;
import minetweaker.mc1710.item.MCItemStack;

/** Shared legacy CraftTweaker conversion and undo behavior for both workstation catalogs. */
final class WorkstationTweakerSupport {

    private WorkstationTweakerSupport() {}

    static IntegratedWorkingStationRecipe recipe(IItemStack output, IIngredient[] inputs, int energy, int ticks,
        ILiquidStack inputFluid, ILiquidStack outputFluid, int maxInputs) {
        if (inputs == null || inputs.length > maxInputs)
            throw new IllegalArgumentException("Use an item input array with at most " + maxInputs + " entries");
        if (energy < 0) throw new IllegalArgumentException("Energy must be non-negative (total AE per operation)");
        if (ticks <= 0 || ticks > LargeWorkstationRecipe.MAX_PROCESSING_TICKS)
            throw new IllegalArgumentException("Processing ticks must be between 1 and 200");
        List<SizedIngredient> items = new ArrayList<>();
        for (IIngredient input : inputs) items.add(ingredient(input));
        FluidStack fluidIn = liquid(inputFluid, true);
        FluidStack fluidOut = liquid(outputFluid, false);
        ItemStack itemOut = output == null ? null : item(output);
        if (itemOut != null && (itemOut.stackSize <= 0 || itemOut.getItemDamage() == OreDictionary.WILDCARD_VALUE))
            throw new IllegalArgumentException("Item output must have a positive amount and concrete metadata");
        if (items.isEmpty() && fluidIn == null) throw new IllegalArgumentException("Recipe needs an input");
        if (itemOut == null && fluidOut == null) throw new IllegalArgumentException("Recipe needs an output");
        return new IntegratedWorkingStationRecipe(
            items,
            fluidIn == null ? new SizedFluidIngredient(SourceFluidIngredient.empty(), 1)
                : new SizedFluidIngredient(SourceFluidIngredient.of(fluidIn.getFluid()), fluidIn.amount),
            itemOut,
            fluidOut,
            energy,
            ticks);
    }

    private static SizedIngredient ingredient(IIngredient input) {
        if (input == null || input.getAmount() <= 0)
            throw new IllegalArgumentException("Item inputs must be non-null with positive amounts");
        if (input.hasTransformers())
            throw new IllegalArgumentException("Ingredient transformations and reusable tools are not supported");
        List<IItemStack> candidates = input.getItems();
        if (candidates == null || candidates.isEmpty())
            throw new IllegalArgumentException("Item ingredient must have registered item examples");
        List<ItemStack> examples = new ArrayList<>();
        for (IItemStack candidate : candidates) examples.add(item(candidate));
        int amount = input.getAmount();
        return new SizedIngredient(SourceIngredient.matching(examples, stack -> {
            ItemStack probe = stack.copy();
            // Quantity allocation belongs to the machine. CT's sized ingredients still expect their base amount.
            probe.stackSize = amount;
            return input.matches(new MCItemStack(probe));
        }), amount);
    }

    private static ItemStack item(IItemStack stack) {
        if (stack == null || !(stack.getInternal() instanceof ItemStack))
            throw new IllegalArgumentException("Invalid Minecraft item stack");
        ItemStack result = ((ItemStack) stack.getInternal()).copy();
        if (result.getItem() == null) throw new IllegalArgumentException("Unregistered item");
        return result;
    }

    private static FluidStack liquid(ILiquidStack stack, boolean input) {
        if (stack == null) return null;
        if (!(stack.getInternal() instanceof FluidStack)) throw new IllegalArgumentException("Invalid fluid stack");
        FluidStack result = ((FluidStack) stack.getInternal()).copy();
        if (result.amount <= 0 || result.getFluid() == null)
            throw new IllegalArgumentException("Fluid must be registered with a positive amount");
        if (input && result.tag != null)
            throw new IllegalArgumentException("Fluid input NBT is not supported by the workstation");
        return result;
    }

    static ResourceLocation recipeId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Use a namespaced recipe id, for example pack:diamond");
        return ResourceLocation.parse(id);
    }

    static boolean matchesOutput(IntegratedWorkingStationRecipe recipe, IIngredient output) {
        ItemStack item = recipe.itemOutput();
        if (item != null && output.matches(new MCItemStack(item))) return true;
        FluidStack fluid = recipe.fluidOutput();
        return fluid != null && output.matches(new minetweaker.mc1710.liquid.MCLiquidStack(fluid));
    }

    static final class RecipeAction implements IUndoableAction {

        private final String description;
        private final Supplier<Runnable> edit;
        private Runnable undo;

        RecipeAction(String description, Supplier<Runnable> edit) {
            this.description = description;
            this.edit = edit;
        }

        @Override
        public void apply() {
            undo = edit.get();
        }

        @Override
        public boolean canUndo() {
            return true;
        }

        @Override
        public void undo() {
            if (undo != null) {
                undo.run();
                undo = null;
            }
        }

        @Override
        public String describe() {
            return description;
        }

        @Override
        public String describeUndo() {
            return "Undo: " + description;
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }
}
