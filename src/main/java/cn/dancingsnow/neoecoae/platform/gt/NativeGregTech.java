package cn.dancingsnow.neoecoae.platform.gt;

import net.minecraft.tileentity.TileEntity;

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
        GregTechMachineRecipes.register();
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

    /** Called by the optional GT mixin after the native recipe check has accepted or rejected a cycle. */
    public static void recipeChecked(Object machine, boolean successful) {
        GregTechMachineAccess.recipeChecked(machine, successful);
    }

    /** Called by the optional GT mixin after native NBT restoration, before a new recipe check occurs. */
    public static void recipeLoaded(Object machine) {
        GregTechMachineAccess.recipeLoaded(machine);
    }

    /** Opens a GT machine's or hatch's own GUI the way a right click would, without held-item side effects. */
    public static boolean openGui(TileEntity tile, net.minecraft.entity.player.EntityPlayer player) {
        return isAvailable() && GregTechMachineAccess.openGui(tile, player);
    }

    /** Localized name of a GT meta tile entity (hatch, bus, machine), or null for any other tile. */
    public static String machineName(TileEntity tile) {
        return isAvailable() ? GregTechMachineAccess.machineName(tile) : null;
    }

    /** The GT block hosting a meta tile entity (e.g. a pattern hatch acting as crafting medium), else null. */
    public static TileEntity hostTile(Object machine) {
        return isAvailable() ? GregTechMachineAccess.hostTile(machine) : null;
    }

    public static Object metaTile(TileEntity tile) {
        return isAvailable() ? GregTechMachineAccess.metaTile(tile) : null;
    }

    public static boolean isActiveMachine(Object machine) {
        return isAvailable() && GregTechMachineAccess.isActiveMachine(machine);
    }

    /** Input parts and proxy masters of a formed controller, shared by route and machine-status discovery. */
    public static java.util.List<TileEntity> inputTiles(Object machine) {
        return isAvailable() ? GregTechMachineAccess.inputs(machine) : java.util.Collections.emptyList();
    }

    /** Mirrors sharing a pattern bank; callers resolve their controllers under the normal scan budget. */
    public static java.util.List<TileEntity> craftingInputProxies(TileEntity tile) {
        return isAvailable() ? GregTechMachineAccess.craftingInputProxies(tile) : java.util.Collections.emptyList();
    }

    @com.github.bsideup.jabel.Desugar
    public record MachineSnapshot(int metaTileId, String machineClass, boolean active, long storedEu, long euCapacity,
        int progress, int maxProgress, int euPerTick) {}
}
