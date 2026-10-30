package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.crafting.ICraftingIconProvider;
import appeng.helpers.IInterfaceHost;
import cn.dancingsnow.neoecoae.compat.proghatches.upload.ProgrammableHatchUpload;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternRouteProbe;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

/**
 * Reads the machine routes behind a pattern host on every call: GT pattern hatches, and GT machines or hatches facing
 * an ME interface. Multiblock ownership comes from a per-world index rebuilt at most once per interval, so hint
 * refreshes never rescan the world per candidate.
 */
public final class GtPatternRoutes implements PatternRouteProbe {

    private static final long OWNER_INDEX_TICKS = 100;

    private final Map<World, OwnerIndex> owners = new WeakHashMap<>();
    /** Recipe-lookup verdicts shared by the targets of one pass; never holds machine state. */
    private final GtRecipeVerifier.Cache verdicts = new GtRecipeVerifier.Cache();

    private GtPatternRoutes() {}

    public static PatternRouteProbe create() {
        return new GtPatternRoutes();
    }

    @Override
    public void invalidate() {
        // Ownership is time-bounded instead; structure changes are re-read at submit through the live routes.
        verdicts.clear();
    }

    @Override
    public PatternUploadTarget.Kind kind(Object host) {
        if (host instanceof MTEHatchCraftingInputME)
            return ((MTEHatchCraftingInputME) host).supportsFluids() ? PatternUploadTarget.Kind.GT_CRAFTING_INPUT
                : PatternUploadTarget.Kind.GT_CRAFTING_INPUT_BUS;
        return host instanceof IDualInputHatch ? PatternUploadTarget.Kind.GT_CRAFTING_INPUT : null;
    }

    private static final class Route {

        final RecipeMap<?> map;
        final ItemStack circuit;
        final ItemStack icon;
        final String name;
        final boolean programmingCover;

        Route(RecipeMap<?> map, ItemStack circuit, ItemStack icon, String name, boolean programmingCover) {
            this.map = map;
            this.circuit = circuit;
            this.icon = icon;
            this.name = name;
            this.programmingCover = programmingCover;
        }
    }

    private static final class Candidate {

        final MatchResult.Level level;
        final String reason;
        final Route route;
        final ItemStack circuit;

        Candidate(MatchResult.Level level, String reason, Route route, ItemStack circuit) {
            this.level = level;
            this.reason = reason;
            this.route = route;
            this.circuit = circuit;
        }
    }

    @Override
    public MatchResult match(Object host, PatternContext context, boolean programmable) {
        List<Route> routes = routes(host);
        if (routes.isEmpty()) return null;
        String hintMap = context.hint == null ? null : context.hint.getRecipeMapId();
        if (hintMap != null && !knownMap(hintMap)) hintMap = null;
        ItemStack hintCircuit = context.hint == null ? null : GtCircuits.unwrap(context.hint.getCircuit());
        ItemStack patternCircuit = GtCircuits.patternCircuit(context.details);
        boolean programmingCircuit = GtCircuits.hasProgrammingCircuit(context.details);
        Candidate best = null;
        for (Route route : routes) {
            Candidate candidate = evaluate(
                verdicts,
                route,
                context,
                hintMap,
                hintCircuit,
                patternCircuit,
                programmingCircuit,
                programmable || route.programmingCover);
            if (best == null || candidate.level.ordinal() < best.level.ordinal()) best = candidate;
        }
        String signature = best.route.map.unlocalizedName + "#" + GtCircuits.number(best.circuit);
        return new MatchResult(
            best.level,
            best.reason,
            best.circuit,
            best.route.name,
            best.route.icon,
            signature,
            best.route.programmingCover || programmable && programmingCircuit);
    }

    private static Candidate evaluate(GtRecipeVerifier.Cache verdicts, Route route, PatternContext context,
        String hintMap, ItemStack hintCircuit, ItemStack patternCircuit, boolean programmingCircuit,
        boolean canProgram) {
        if (hintMap != null && !hintMap.equals(route.map.unlocalizedName)) return new Candidate(
            MatchResult.Level.WRONG_RECIPE,
            "gui.neoecoae.pattern_upload.reason.hint_map",
            route,
            route.circuit);
        ItemStack selector = null;
        String conflict = null;
        if (patternCircuit != null) {
            // The pattern carries its own selector; the machine slot and NEI context must not disagree with it.
            if (hintCircuit != null && !GtCircuits.same(hintCircuit, patternCircuit))
                conflict = "gui.neoecoae.pattern_upload.reason.hint_circuit";
            else if (programmingCircuit && !canProgram)
                conflict = "gui.neoecoae.pattern_upload.reason.needs_programming";
        } else {
            if (route.circuit != null && hintCircuit != null && !GtCircuits.same(route.circuit, hintCircuit))
                conflict = "gui.neoecoae.pattern_upload.reason.machine_circuit";
            selector = route.circuit != null ? route.circuit : hintCircuit;
        }
        ItemStack shown = patternCircuit != null ? patternCircuit : selector;
        GtRecipeVerifier.Verdict verdict = verdicts.verify(route.map, context.details, selector);
        if (verdict == GtRecipeVerifier.Verdict.NONE && patternCircuit == null
            && selector == null
            && verdicts.matchesWithAnyCircuit(route.map, context.details))
            return new Candidate(
                MatchResult.Level.UNVERIFIED,
                "gui.neoecoae.pattern_upload.reason.circuit_unknown",
                route,
                null);
        switch (verdict) {
            case EXACT:
                if (conflict != null) return new Candidate(MatchResult.Level.CIRCUIT_CONFLICT, conflict, route, shown);
                if (patternCircuit == null && route.circuit == null && hintCircuit != null) return new Candidate(
                    MatchResult.Level.CIRCUIT_CONFLICT,
                    "gui.neoecoae.pattern_upload.reason.machine_circuit_missing",
                    route,
                    shown);
                return new Candidate(MatchResult.Level.EXACT, null, route, shown);
            case PROBABILISTIC:
                return new Candidate(
                    MatchResult.Level.UNVERIFIED,
                    "gui.neoecoae.pattern_upload.reason.probabilistic",
                    route,
                    shown);
            case RATIO_MISMATCH:
                return new Candidate(
                    MatchResult.Level.RATIO_MISMATCH,
                    "gui.neoecoae.pattern_upload.reason.ratio",
                    route,
                    shown);
            default:
                return new Candidate(
                    MatchResult.Level.WRONG_RECIPE,
                    "gui.neoecoae.pattern_upload.reason.recipe",
                    route,
                    shown);
        }
    }

    private static boolean knownMap(String id) {
        try {
            return RecipeMap.ALL_RECIPE_MAPS.containsKey(id) || RecipeMap.getFromOldIdentifier(id) != null;
        } catch (RuntimeException unknown) {
            return false;
        }
    }

    private List<Route> routes(Object host) {
        List<Route> routes = new ArrayList<>();
        try {
            if (host instanceof IMetaTileEntity) {
                IMetaTileEntity machine = (IMetaTileEntity) host;
                ItemStack circuit = circuit(machine);
                RecipeMap<?> map = recipeMap(machine);
                ItemStack icon = icon(machine);
                if (map != null) add(routes, map, circuit, icon, name(icon, machine), false);
                if (machine instanceof IDualInputHatch) {
                    MTEMultiBlockBase owner = owner(machine);
                    if (owner != null && owner.getRecipeMap() != null) {
                        ItemStack ownerIcon = icon(owner);
                        add(routes, owner.getRecipeMap(), circuit, ownerIcon, name(ownerIcon, owner), false);
                    }
                }
            }
            if (host instanceof IInterfaceHost) {
                TileEntity tile = ((IInterfaceHost) host).getTileEntity();
                EnumSet<ForgeDirection> targets = ((IInterfaceHost) host).getTargets();
                if (tile != null && tile.getWorldObj() != null && targets != null) for (ForgeDirection side : targets) {
                    if (side == null || side == ForgeDirection.UNKNOWN) continue;
                    TileEntity adjacent = tile.getWorldObj()
                        .getTileEntity(
                            tile.xCoord + side.offsetX,
                            tile.yCoord + side.offsetY,
                            tile.zCoord + side.offsetZ);
                    if (!(adjacent instanceof IGregTechTileEntity)) continue;
                    IMetaTileEntity machine = ((IGregTechTileEntity) adjacent).getMetaTileEntity();
                    if (!(machine instanceof MTEHatchInput || machine instanceof MTEHatchInputBus
                        || machine instanceof RecipeMapWorkable)) continue;
                    RecipeMap<?> map = recipeMap(machine);
                    MTEMultiBlockBase owner = map == null ? owner(machine) : null;
                    if (owner != null) map = owner.getRecipeMap();
                    if (map == null) continue;
                    ItemStack icon = icon(owner != null ? owner : machine);
                    add(
                        routes,
                        map,
                        circuit(machine),
                        icon,
                        name(icon, owner != null ? owner : machine),
                        hasProgrammingCover((IGregTechTileEntity) adjacent));
                }
            }
        } catch (RuntimeException rebuilding) {
            // A machine observed while its structure or inventory initializes exposes no route this time.
        }
        return routes;
    }

    private static void add(List<Route> routes, RecipeMap<?> map, ItemStack circuit, ItemStack icon, String name,
        boolean cover) {
        for (Route route : routes)
            if (route.map == map && (route.circuit == null ? circuit == null : GtCircuits.same(route.circuit, circuit)))
                return;
        routes.add(new Route(map, circuit, icon, name, cover));
    }

    private static RecipeMap<?> recipeMap(IMetaTileEntity machine) {
        if (machine instanceof MTEHatchInput) return ((MTEHatchInput) machine).mRecipeMap;
        if (machine instanceof MTEHatchInputBus) return ((MTEHatchInputBus) machine).mRecipeMap;
        if (machine instanceof RecipeMapWorkable) return ((RecipeMapWorkable) machine).getRecipeMap();
        return null;
    }

    private static ItemStack circuit(IMetaTileEntity machine) {
        if (!(machine instanceof IConfigurationCircuitSupport) || !(machine instanceof MetaTileEntity)) return null;
        int slot = ((IConfigurationCircuitSupport) machine).getCircuitSlot();
        var inventory = ((MetaTileEntity) machine).getInventoryHandler();
        if (slot < 0 || inventory == null || slot >= inventory.getSlots()) return null;
        return GtCircuits.unwrap(inventory.getStackInSlot(slot));
    }

    private static boolean hasProgrammingCover(IGregTechTileEntity tile) {
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS)
            if (ProgrammableHatchUpload.isCover(tile.getCoverAtSide(side))) return true;
        return false;
    }

    private static ItemStack icon(IMetaTileEntity machine) {
        try {
            if (machine instanceof ICraftingIconProvider) {
                ItemStack icon = ((ICraftingIconProvider) machine).getMachineCraftingIcon();
                if (icon != null) return icon.copy();
            }
            return machine.getStackForm(1);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static String name(ItemStack icon, IMetaTileEntity machine) {
        if (icon != null) {
            String name = icon.getUnlocalizedName();
            if (name != null && !name.isEmpty()) return name + ".name";
        }
        return machine.getMetaName();
    }

    private MTEMultiBlockBase owner(IMetaTileEntity part) {
        IGregTechTileEntity base = part.getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (world == null) return null;
        OwnerIndex index = owners.get(world);
        long now = world.getTotalWorldTime();
        if (index == null || now - index.builtAt > OWNER_INDEX_TICKS || now < index.builtAt) {
            index = OwnerIndex.build(world, now);
            owners.put(world, index);
        }
        MTEMultiBlockBase owner = index.owners.get(part);
        return owner != null && owner.mMachine
            && owner.getBaseMetaTileEntity() != null
            && !owner.getBaseMetaTileEntity()
                .isDead() ? owner : null;
    }

    /** Formed controller per input part, built by one bounded pass over the loaded tiles. */
    private static final class OwnerIndex {

        final long builtAt;
        final Map<Object, MTEMultiBlockBase> owners = new IdentityHashMap<>();

        private OwnerIndex(long builtAt) {
            this.builtAt = builtAt;
        }

        static OwnerIndex build(World world, long now) {
            OwnerIndex index = new OwnerIndex(now);
            for (Object loaded : new ArrayList<>(world.loadedTileEntityList)) {
                if (!(loaded instanceof IGregTechTileEntity)) continue;
                IMetaTileEntity meta = ((IGregTechTileEntity) loaded).getMetaTileEntity();
                if (!(meta instanceof MTEMultiBlockBase)) continue;
                MTEMultiBlockBase controller = (MTEMultiBlockBase) meta;
                if (!controller.mMachine) continue;
                if (controller.mDualInputHatches != null)
                    for (Object hatch : controller.mDualInputHatches) index.owners.putIfAbsent(hatch, controller);
                if (controller.mInputBusses != null)
                    for (Object bus : controller.mInputBusses) index.owners.putIfAbsent(bus, controller);
                if (controller.mInputHatches != null)
                    for (Object hatch : controller.mInputHatches) index.owners.putIfAbsent(hatch, controller);
            }
            return index;
        }
    }
}
