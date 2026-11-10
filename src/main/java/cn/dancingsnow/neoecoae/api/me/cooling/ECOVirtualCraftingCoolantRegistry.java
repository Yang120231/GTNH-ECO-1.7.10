package cn.dancingsnow.neoecoae.api.me.cooling;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import cn.dancingsnow.neoecoae.platform.fluid.FluidStack;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/**
 * Additional fluids allowed to fund virtual crafting. ECO and GTNH cryotheum are always allowed.
 *
 * <p>
 * Register fluid IDs during common setup; the fluids need not be resolved yet. Registration only grants
 * permission: a matching cooling recipe must still provide tier-9 cooling and enough coolant points.
 * Execution and server-side diagnostics read the registry live, including already buffered coolant.
 * </p>
 */
public final class ECOVirtualCraftingCoolantRegistry {

    public static final ResourceLocation CRYOTHEUM_SOLUTION = ResourceLocation.parse("neoecoae:cryotheum_solution");
    private static final ResourceLocation GTNH_CRYOTHEUM = ResourceLocation.parse("minecraft:cryotheum");
    private static final CopyOnWriteArraySet<ResourceLocation> CUSTOM_COOLANTS = new CopyOnWriteArraySet<>();

    private ECOVirtualCraftingCoolantRegistry() {}

    public static void register(ResourceLocation fluidId) {
        Objects.requireNonNull(fluidId, "fluidId");
        if (!CRYOTHEUM_SOLUTION.equals(fluidId) && !GTNH_CRYOTHEUM.equals(fluidId)) CUSTOM_COOLANTS.add(fluidId);
    }

    /** Removes a custom registration. Both built-in cryotheum fluids remain allowed. */
    public static void unregister(ResourceLocation fluidId) {
        if (fluidId != null) CUSTOM_COOLANTS.remove(fluidId);
    }

    /** Stable display order, with the built-in coolant first. */
    public static List<ResourceLocation> registeredFluidIds() {
        var ids = new java.util.ArrayList<ResourceLocation>();
        ids.add(CRYOTHEUM_SOLUTION);
        ids.add(GTNH_CRYOTHEUM);
        CUSTOM_COOLANTS.stream()
            .sorted()
            .forEach(ids::add);
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(ids);
    }

    public static boolean isAllowed(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return false;
        ResourceLocation id = ResourceLocation.parse(
            fluid.getFluid()
                .getName()
                .contains(":")
                    ? fluid.getFluid()
                        .getName()
                    : "minecraft:" + fluid.getFluid()
                        .getName());
        return cn.dancingsnow.neoecoae.platform.recipe.SourceFluids.isCryotheum(fluid.getFluid())
            || CUSTOM_COOLANTS.contains(id);
    }
}
