package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.util.ArrayList;
import java.util.Collections;
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
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.IInterfaceHost;
import cn.dancingsnow.neoecoae.compat.proghatches.upload.ProgrammableHatchUpload;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternRouteProbe;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.crafting.upload.RouteHint;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;
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
import tectech.recipe.TecTechRecipeMaps;
import tectech.thing.metaTileEntity.multi.bec.MTEBECIONode;

/**
 * Reads the machine routes behind a pattern host on every call: GT pattern hatches, and GT machines or hatches facing
 * an ME interface. Multiblock ownership comes from one per-world index per discovery, so candidates share its scan.
 */
public final class GtPatternRoutes implements PatternRouteProbe {

    private static final long OWNER_INDEX_TICKS = 100;
    /**
     * Separates a route name's machine key from the key of the mode it runs in; {@code PatternUploadScreen#text}
     * translates each part.
     */
    public static final char MODE_SEPARATOR = '';

    private final Map<World, OwnerIndex> owners = new WeakHashMap<>();
    /** Recipe-lookup verdicts shared by the targets of one pass; never holds machine state. */
    private final GtRecipeVerifier.Cache verdicts = new GtRecipeVerifier.Cache();

    private GtPatternRoutes() {}

    public static PatternRouteProbe create() {
        return new GtPatternRoutes();
    }

    /** Server entry points stay independent of NEI's client-only handler classes. */
    public static boolean isKnownMap(String id) {
        return knownMap(id) != null;
    }

    public static void updateSelection(RouteHint hint, IAEStack<?>[] inputs) {
        if (GtCircuits.conflictingCircuits(inputs)) return;
        ItemStack selected = GtCircuits.patternCircuit(inputs);
        if (selected != null) hint.selectCircuit(selected);
        else if (GtCircuits.resetsSelector(inputs)) hint.selectCircuit(null);
    }

    /** One effective request for matching, binding and display, including selectors expanded from tunnel inputs. */
    public static ItemStack selectedCircuit(PatternContext context) {
        ItemStack selected = GtCircuits.patternCircuit(context.recipeInputs);
        if (selected != null) return selected;
        if (GtCircuits.resetsSelector(context.recipeInputs)) return null;
        return context.hint == null ? null : GtCircuits.unwrap(context.hint.getCircuit());
    }

    public static String selectionKey(PatternContext context) {
        if (GtCircuits.conflictingCircuits(context.recipeInputs)) return "!";
        ItemStack selected = selectedCircuit(context);
        if (selected != null) return GtCircuits.number(selected);
        if (GtCircuits.resetsSelector(context.recipeInputs)) return "-";
        return context.hint == null ? "?" : context.hint.selectionKey();
    }

    @Override
    public void invalidate() {
        owners.clear();
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
        final IMetaTileEntity receiver;
        final boolean programmingCover;
        final boolean programmable;

        Route(RecipeMap<?> map, ItemStack circuit, ItemStack icon, String name, IMetaTileEntity receiver,
            boolean programmingCover, boolean programmable) {
            this.map = map;
            this.circuit = circuit;
            this.icon = icon;
            this.name = name;
            this.receiver = receiver;
            this.programmingCover = programmingCover;
            this.programmable = programmable;
        }

        String role() {
            return receiver == null ? ""
                : receiver.getClass()
                    .getName();
        }
    }

    private static final class Candidate {

        final MatchResult.Level level;
        final String reason;
        final Route route;
        final ItemStack circuit;
        final String categoryId;
        final String selectionKey;
        final boolean programming;

        Candidate(MatchResult.Level level, String reason, Route route, ItemStack circuit, String categoryId,
            String selectionKey, boolean programming) {
            this.level = level;
            this.reason = reason;
            this.route = route;
            this.circuit = circuit;
            this.categoryId = categoryId;
            this.selectionKey = selectionKey;
            this.programming = programming;
        }

        MatchResult result() {
            String signature = (route.map == null ? "" : route.map.unlocalizedName) + "#"
                + GtCircuits.number(circuit)
                + "#"
                + route.role();
            return new MatchResult(
                level,
                reason,
                circuit,
                route.name,
                route.icon,
                signature,
                programming,
                categoryId,
                selectionKey);
        }
    }

    @Override
    public MatchResult match(Object host, PatternContext context, boolean programmable) {
        if (host instanceof IDualInputHatch && !((IDualInputHatch) host).supportsFluids()
            && context.recipeInputs != null)
            for (IAEStack<?> input : context.recipeInputs) if (input instanceof IAEFluidStack
                && input.getStackSize() > 0)
                return MatchResult.of(MatchResult.Level.INCOMPATIBLE, "gui.neoecoae.pattern_upload.reason.fluid_input");
        List<Route> routes = routes(host);
        if (routes.isEmpty()) return null;
        RecipeMap<?> sourceMap = context.hint != null && context.hint.hasRecipeMap()
            ? knownMap(context.hint.getRecipeMapId())
            : null;
        ItemStack patternCircuit = GtCircuits.patternCircuit(context.recipeInputs);
        ItemStack selected = selectedCircuit(context);
        boolean programmingCircuit = GtCircuits.hasProgrammingCircuit(context.recipeInputs);
        boolean conflict = GtCircuits.conflictingCircuits(context.recipeInputs);
        Candidate best = null;
        String resolved = null;
        boolean ambiguous = false;
        for (Route route : routes) {
            Candidate candidate = evaluate(
                route,
                context,
                sourceMap,
                selected,
                patternCircuit,
                programmingCircuit,
                conflict,
                programmable || route.programmingCover || route.programmable);
            if (candidate.level == MatchResult.Level.ROUTED
                || MatchResult.allowsMapping(candidate.level, candidate.reason)) {
                String signature = candidate.result().routeSignature;
                if (resolved != null && !resolved.equals(signature)) ambiguous = true;
                resolved = signature;
            }
            if (best == null || candidate.level.rank() < best.level.rank()) best = candidate;
        }
        MatchResult result = best.result();
        return ambiguous
            ? new MatchResult(
                MatchResult.Level.UNVERIFIED,
                "gui.neoecoae.pattern_upload.reason.route_ambiguous",
                result.circuit,
                result.routeName,
                result.routeIcon,
                result.routeSignature,
                result.programmingCover,
                result.categoryId,
                result.selectionKey)
            : result;
    }

    private Candidate evaluate(Route route, PatternContext context, RecipeMap<?> sourceMap, ItemStack selected,
        ItemStack patternCircuit, boolean programmingCircuit, boolean conflictingCircuits, boolean canProgram) {
        boolean sourced = context.hint != null && !context.hint.isEmpty();
        String category = sourced && !context.hint.getCategoryId()
            .isEmpty() ? context.hint.getCategoryId() : route.map == null ? "" : "gt:" + route.map.unlocalizedName;
        String selection = selectionKey(context);
        boolean programming = route.programmingCover || programmingCircuit && canProgram;
        boolean categoryUnknown = sourced && !context.hint.hasRecipeMap() && !context.hint.hasMachineTypes();
        if (conflictingCircuits) return candidate(
            MatchResult.Level.CIRCUIT_CONFLICT,
            "circuits_multiple",
            route,
            selected,
            category,
            selection,
            programming);
        if (route.map == null) return candidate(
            MatchResult.Level.UNVERIFIED,
            "no_controller",
            route,
            selected,
            category,
            selection,
            programming);
        if (sourced && context.hint.hasRecipeMap()) {
            // An invalid captured source is not permission to infer a different map from the edited pattern.
            if (sourceMap == null) return candidate(
                MatchResult.Level.UNVERIFIED,
                "source_map_unknown",
                route,
                selected,
                category,
                selection,
                programming);
            if (sourceMap != route.map) return candidate(
                MatchResult.Level.WRONG_RECIPE,
                "hint_map",
                route,
                selected,
                category,
                selection,
                programming);
        } else if (sourced && !categoryUnknown
            && !context.hint.matchesMachine(route.icon)
            && (route.receiver == null || !context.hint.matchesMachine(route.receiver.getStackForm(1))))
            return candidate(
                MatchResult.Level.WRONG_RECIPE,
                "hint_map",
                route,
                selected,
                category,
                selection,
                programming);
        if (route.receiver == null) return candidate(
            MatchResult.Level.UNVERIFIED,
            "no_controller",
            route,
            selected,
            category,
            selection,
            programming);
        if (route.map == TecTechRecipeMaps.condensateAssemblingRecipes && !(route.receiver instanceof MTEBECIONode))
            return candidate(
                MatchResult.Level.INCOMPATIBLE,
                "bec_role",
                route,
                selected,
                category,
                selection,
                programming);

        if (!sourced) {
            GtRecipeVerifier.Inference inferred = verdicts.infer(route.map, context);
            if (!inferred.complete) return candidate(
                MatchResult.Level.UNVERIFIED,
                "search_incomplete",
                route,
                selected,
                category,
                selection,
                programming);
            if (!inferred.found()) return candidate(
                MatchResult.Level.WRONG_RECIPE,
                "recipe",
                route,
                selected,
                category,
                selection,
                programming);
            if (!inferred.unique()) {
                if (programmingCircuit && !canProgram) return candidate(
                    MatchResult.Level.CIRCUIT_CONFLICT,
                    "needs_programming",
                    route,
                    route.circuit,
                    category,
                    selection,
                    programming);
                if (!inferred.allows(route.circuit)) return candidate(
                    MatchResult.Level.CIRCUIT_CONFLICT,
                    route.circuit == null ? "machine_circuit_missing" : "machine_circuit",
                    route,
                    route.circuit,
                    category,
                    selection,
                    programming);
                return candidate(
                    MatchResult.Level.UNVERIFIED,
                    "circuit_unknown",
                    route,
                    route.circuit,
                    category,
                    selection,
                    programming);
            }
            selected = patternCircuit != null ? patternCircuit : inferred.circuit();
        }
        if (programmingCircuit && !canProgram) return candidate(
            MatchResult.Level.CIRCUIT_CONFLICT,
            "needs_programming",
            route,
            selected,
            category,
            selection,
            programming);
        if (selected != null && route.circuit != null
            && !GtCircuits.same(selected, route.circuit)
            && !(GtCircuits.hasProgrammingSelector(context.recipeInputs) && canProgram))
            return candidate(
                MatchResult.Level.CIRCUIT_CONFLICT,
                "machine_circuit",
                route,
                selected,
                category,
                selection,
                programming);
        if (selected != null && route.circuit == null && patternCircuit == null) return candidate(
            MatchResult.Level.CIRCUIT_CONFLICT,
            "machine_circuit_missing",
            route,
            selected,
            category,
            selection,
            programming);
        if (selected != null && patternCircuit == null && programmingCircuit) return candidate(
            MatchResult.Level.CIRCUIT_CONFLICT,
            "machine_circuit_missing",
            route,
            selected,
            category,
            selection,
            programming);
        if (sourced && !context.hint.hasRecipeMap() && selected == null && route.circuit != null) return candidate(
            MatchResult.Level.UNVERIFIED,
            "circuit_unknown",
            route,
            route.circuit,
            category,
            selection,
            programming);
        if (categoryUnknown) return candidate(
            MatchResult.Level.UNVERIFIED,
            "no_route",
            route,
            selected,
            category,
            selection,
            programming);
        return candidate(MatchResult.Level.ROUTED, null, route, selected, category, selection, programming);
    }

    private static Candidate candidate(MatchResult.Level level, String reason, Route route, ItemStack circuit,
        String category, String selection, boolean programming) {
        return new Candidate(
            level,
            reason == null ? null : "gui.neoecoae.pattern_upload.reason." + reason,
            route,
            circuit,
            category,
            selection,
            programming);
    }

    private static RecipeMap<?> knownMap(String id) {
        try {
            return RecipeMap.getFromOldIdentifier(id);
        } catch (RuntimeException unknown) {
            return null;
        }
    }

    private List<Route> routes(Object host) {
        List<Route> routes = new ArrayList<>();
        try {
            if (host instanceof IMetaTileEntity) addMachine(routes, (IMetaTileEntity) host);
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
                    if (machine instanceof MTEHatchInput || machine instanceof MTEHatchInputBus
                        || machine instanceof RecipeMapWorkable) addMachine(routes, machine);
                }
            }
        } catch (RuntimeException rebuilding) {
            // A partially inspected host is never considered a unique processing receiver.
            routes.clear();
        }
        return routes;
    }

    private void addMachine(List<Route> routes, IMetaTileEntity machine) {
        ItemStack circuit = circuit(machine);
        boolean cover = hasProgrammingCover(machine.getBaseMetaTileEntity());
        boolean programmable = ProgrammableHatchUpload.isProgrammingInput(machine);
        List<MTEMultiBlockBase> controllers = machine instanceof RecipeMapWorkable ? Collections.emptyList()
            : owners(machine);
        if (!controllers.isEmpty()) {
            for (MTEMultiBlockBase controller : controllers) {
                RecipeMap<?> map = controller.getRecipeMap();
                if (map == null) continue;
                ItemStack icon = icon(controller);
                add(routes, map, circuit, icon, name(icon, controller, map), controller, cover, programmable);
            }
        } else {
            RecipeMap<?> map = recipeMap(machine);
            ItemStack icon = icon(machine);
            add(
                routes,
                map,
                circuit,
                icon,
                name(icon, machine, map),
                machine instanceof RecipeMapWorkable ? machine : null,
                cover,
                programmable);
        }
    }

    private static void add(List<Route> routes, RecipeMap<?> map, ItemStack circuit, ItemStack icon, String name,
        IMetaTileEntity receiver, boolean cover, boolean programmable) {
        // The same map and circuit can be reached through different input parts. Keep programming capability:
        // an earlier unprogrammed bus (or fluid hatch) must not hide a later bus carrying a programming cover.
        for (Route route : routes) if (route.map == map && route.programmingCover == cover
            && route.programmable == programmable
            && route.receiver == receiver
            && (route.circuit == null ? circuit == null : GtCircuits.same(route.circuit, circuit))) return;
        routes.add(new Route(map, circuit, icon, name, receiver, cover, programmable));
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
        if (tile == null || !ProgrammableHatchUpload.isAvailable()) return false;
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

    /**
     * The machine's name, followed by its current recipe map when it switches between modes, so the same machine in
     * two modes shows as two distinct routes.
     */
    private static String name(ItemStack icon, IMetaTileEntity machine, RecipeMap<?> map) {
        String name = name(icon, machine);
        if (!(machine instanceof MTEMultiBlockBase) || map == null || map.unlocalizedName == null) return name;
        try {
            if (!((MTEMultiBlockBase) machine).supportsMachineModeSwitch()) return name;
        } catch (RuntimeException | LinkageError unknown) {
            return name;
        }
        return name + MODE_SEPARATOR + map.unlocalizedName;
    }

    private static String name(ItemStack icon, IMetaTileEntity machine) {
        if (icon != null) {
            String name = icon.getUnlocalizedName();
            if (name != null && !name.isEmpty()) return name + ".name";
        }
        return machine.getMetaName();
    }

    private List<MTEMultiBlockBase> owners(IMetaTileEntity part) {
        IGregTechTileEntity base = part.getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (world == null) return Collections.emptyList();
        OwnerIndex index = owners.get(world);
        long now = world.getTotalWorldTime();
        if (index == null || now - index.builtAt > OWNER_INDEX_TICKS || now < index.builtAt) {
            index = OwnerIndex.build(world, now);
            owners.put(world, index);
        }
        List<MTEMultiBlockBase> found = index.owners.get(part);
        if (found == null) return Collections.emptyList();
        List<MTEMultiBlockBase> live = new ArrayList<>(found);
        live.removeIf(
            owner -> !owner.mMachine || owner.getBaseMetaTileEntity() == null
                || owner.getBaseMetaTileEntity()
                    .isDead());
        return live;
    }

    /** Formed controller per input part, built by one bounded pass over the loaded tiles. */
    private static final class OwnerIndex {

        final long builtAt;
        final Map<Object, List<MTEMultiBlockBase>> owners = new IdentityHashMap<>();

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
                for (TileEntity input : NativeGregTech.inputTiles(controller)) {
                    if (!(input instanceof IGregTechTileEntity)) continue;
                    IMetaTileEntity part = ((IGregTechTileEntity) input).getMetaTileEntity();
                    if (part == null) continue;
                    List<MTEMultiBlockBase> linked = index.owners.computeIfAbsent(part, ignored -> new ArrayList<>());
                    if (!linked.contains(controller)) linked.add(controller);
                }
            }
            return index;
        }
    }
}
