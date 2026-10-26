package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonObject;

/** Explicit source fluid identities; unknown tags are never silently broadened. */
public final class SourceFluidIngredient {

    private static final SourceFluidIngredient EMPTY = new SourceFluidIngredient(Collections.emptyList());
    private final List<Fluid> fluids;

    private SourceFluidIngredient(List<Fluid> fluids) {
        this.fluids = Collections.unmodifiableList(new ArrayList<>(fluids));
    }

    public static SourceFluidIngredient empty() {
        return EMPTY;
    }

    public static SourceFluidIngredient of(Fluid fluid) {
        return new SourceFluidIngredient(Collections.singletonList(java.util.Objects.requireNonNull(fluid)));
    }

    public static SourceFluidIngredient of(JsonObject definition) {
        if (definition == null || definition.entrySet()
            .isEmpty()) return empty();
        if (definition.has("fluid")) {
            String id = definition.get("fluid")
                .getAsString();
            Fluid fluid = SourceFluids.fluid(id);
            if (fluid == null) throw new SourceRecipes.Unavailable("unregistered fluid " + id);
            return of(fluid);
        }
        if (definition.has("tag")) {
            String tag = definition.get("tag")
                .getAsString();
            if (tag.equals("minecraft:water")) return of(FluidRegistry.WATER);
            if (tag.equals("minecraft:lava")) return of(FluidRegistry.LAVA);
            throw new SourceRecipes.Unavailable("fluid tag " + tag);
        }
        throw new IllegalArgumentException("Unknown source fluid predicate: " + definition);
    }

    public boolean isEmpty() {
        return fluids.isEmpty();
    }

    public boolean test(FluidStack stack) {
        return stack != null && stack.amount > 0 && accepts(stack.getFluid());
    }

    public boolean accepts(Fluid fluid) {
        return fluid != null && fluids.contains(fluid);
    }

    public List<FluidStack> examples(int amount) {
        List<FluidStack> result = new ArrayList<>();
        for (Fluid fluid : fluids) result.add(new FluidStack(fluid, amount));
        return result;
    }
}
