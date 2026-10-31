package cn.dancingsnow.neoecoae.platform.gt;

import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cpw.mods.fml.common.Loader;

/** Optional entry point: no GregTech type appears on a class loaded by the normal core runtime. */
public final class NativeGregTech {

    private NativeGregTech() {}

    public static boolean isAvailable() {
        return Loader.isModLoaded("gregtech");
    }

    public static void initialize() {
        if (!isAvailable()) return;
        GregTechMachineAccess.verifyApi();
        String version = Loader.instance()
            .getIndexedModList()
            .get("gregtech")
            .getVersion();
        NeoECOAE.LOG.info("NEOECOAE_NATIVE_GT_AVAILABLE version={} reviewed_api=5.09.54.264", version);
        GregTechMachineRecipes.register();
        cn.dancingsnow.neoecoae.platform.forge.DevAcceptance.hooks()
            .gregTechInit();
    }

    public static net.minecraft.item.ItemStack recipeItem(String id, int count) {
        return isAvailable() ? GregTechRecipeMaterials.item(id, count) : null;
    }

    public static net.minecraftforge.fluids.Fluid recipeFluid(String id) {
        return isAvailable() ? GregTechRecipeMaterials.fluid(id) : null;
    }

    /** Reads the actual native machine. Manufacturing remains owned by the GT tile and its recipe map. */
    public static MachineSnapshot inspect(TileEntity tile) {
        return isAvailable() ? GregTechMachineAccess.inspect(tile) : null;
    }

    public static cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechMachineStatus.Sample sample(TileEntity tile) {
        return isAvailable() ? GregTechMachineAccess.sample(tile) : null;
    }

    @com.github.bsideup.jabel.Desugar
    public record MachineSnapshot(int metaTileId, String machineClass, boolean active, long storedEu, long euCapacity,
        int progress, int maxProgress, int euPerTick) {}
}
