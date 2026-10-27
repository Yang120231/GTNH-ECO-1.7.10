package cn.dancingsnow.neoecoae.platform.recipe;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonObject;

/** The core source fluid is a real Forge identity, usable by tanks and native AE2 fluid keys. */
public final class SourceFluids {

    private static Fluid cryotheum;

    private SourceFluids() {}

    public static void initialize() {
        if (FluidRegistry.getFluid("neoecoae:cryotheum_solution") == null) {
            Fluid fluid = createCryotheum();
            if (!FluidRegistry.registerFluid(fluid))
                throw new IllegalStateException("Cannot register core cryotheum solution");
            cryotheum = fluid;
        }
    }

    static Fluid createCryotheum() {
        return new Fluid("neoecoae:cryotheum_solution").setUnlocalizedName("neoecoae.cryotheum_solution");
    }

    public static Fluid ownCryotheum() {
        return cryotheum;
    }

    public static Fluid fluid(String id) {
        if (id.equals("minecraft:water")) return FluidRegistry.WATER;
        if (id.equals("minecraft:lava")) return FluidRegistry.LAVA;
        if (id.startsWith("gtceu:")) return cn.dancingsnow.neoecoae.platform.gt.NativeGregTech.recipeFluid(id);
        return FluidRegistry.getFluid(id);
    }

    public static FluidStack output(JsonObject value) {
        if (value == null || value.entrySet()
            .isEmpty()) return null;
        if (value.has("components")) throw new IllegalArgumentException("Unmapped source fluid components: " + value);
        String id = value.get("id")
            .getAsString();
        Fluid fluid = fluid(id);
        int amount = value.get("amount")
            .getAsInt();
        if (fluid == null) throw new SourceRecipes.Unavailable("unregistered fluid result " + id);
        if (amount <= 0) throw new IllegalArgumentException("Non-positive fluid result amount");
        return new FluidStack(fluid, amount);
    }
}
