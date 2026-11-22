package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.compat.gtnr.upload.CrossRecipePatternUpload;
import cn.dancingsnow.neoecoae.compat.gtnr.upload.CrossRecipePatternUpload.MapSupport;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult.Level;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternRouteProbe;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.crafting.upload.RouteHint;
import cn.dancingsnow.neoecoae.platform.gt.upload.GtRouteDiscovery.Route;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import tectech.recipe.TecTechRecipeMaps;
import tectech.thing.metaTileEntity.multi.bec.MTEBECIONode;

/** Matches a processing pattern against the GT destinations exposed by an upload target. */
public final class GtPatternRoutes implements PatternRouteProbe {

    /** Separates a route's machine translation key from its active mode's recipe map key. */
    public static final char MODE_SEPARATOR = GtRouteDiscovery.MODE_SEPARATOR;

    private final GtRouteDiscovery discovery = new GtRouteDiscovery();
    private final GtRecipeVerifier.Cache verdicts = new GtRecipeVerifier.Cache();
    private final GtRecipeVerifier.Cache virtualCircuitVerdicts = new GtRecipeVerifier.Cache();

    private GtPatternRoutes() {}

    public static PatternRouteProbe create() {
        return new GtPatternRoutes();
    }

    /** Server entry points stay independent of NEI's client-only handler classes. */
    public static boolean isKnownMap(String id) {
        return knownMap(id) != null;
    }

    /**
     * Whether a GTNR pattern tag and an NEI recipe map id name the same map. GTNR tags use the unlocalized name while
     * NEI sources may use GT's old identifier, so the ids are compared as resolved maps.
     */
    public static boolean sameMap(String patternMap, String hintMapId) {
        if (patternMap == null || hintMapId == null) return false;
        if (patternMap.equals(hintMapId)) return true;
        RecipeMap<?> tagged = RecipeMap.ALL_RECIPE_MAPS.get(patternMap);
        return tagged != null && tagged == knownMap(hintMapId);
    }

    public static void updateSelection(RouteHint hint, IAEStack<?>[] inputs) {
        if (GtCircuits.conflictingCircuits(inputs)) return;
        ItemStack selected = GtCircuits.patternCircuit(inputs);
        if (selected != null) hint.selectCircuit(selected);
        else if (GtCircuits.resetsSelector(inputs)) hint.selectCircuit(null);
    }

    /** One effective request for matching, binding and display, including selectors expanded from tunnel inputs. */
    public static ItemStack selectedCircuit(PatternContext context) {
        return selectedCircuit(context, GtCircuits.patternCircuit(context.recipeInputs));
    }

    private static ItemStack selectedCircuit(PatternContext context, ItemStack patternCircuit) {
        if (patternCircuit != null) return patternCircuit;
        if (GtCircuits.resetsSelector(context.recipeInputs)) return null;
        return context.hint == null ? null : GtCircuits.unwrap(context.hint.getCircuit());
    }

    public static String selectionKey(PatternContext context) {
        if (GtCircuits.conflictingCircuits(context.recipeInputs)) return "!";
        return selectionKey(context, selectedCircuit(context));
    }

    private static String selectionKey(PatternContext context, ItemStack selected) {
        if (selected != null) return GtCircuits.number(selected);
        if (GtCircuits.resetsSelector(context.recipeInputs)) return "-";
        return context.hint == null ? "?" : context.hint.selectionKey();
    }

    @Override
    public void invalidate() {
        discovery.invalidate();
        verdicts.clear();
        virtualCircuitVerdicts.clear();
    }

    @Override
    public PatternUploadTarget.Kind kind(Object host) {
        if (host instanceof MTEHatchCraftingInputME hatch)
            return hatch.supportsFluids() ? PatternUploadTarget.Kind.GT_CRAFTING_INPUT
                : PatternUploadTarget.Kind.GT_CRAFTING_INPUT_BUS;
        return host instanceof IDualInputHatch ? PatternUploadTarget.Kind.GT_CRAFTING_INPUT : null;
    }

    @Override
    public MatchResult match(Object host, PatternContext context, boolean programmable) {
        if (rejectsFluidInputs(host, context)) return MatchResult.of(Level.INCOMPATIBLE, reasonKey("fluid_input"));
        List<Route> routes = discovery.find(host);
        if (routes.isEmpty()) return null;

        boolean crossRecipeBuffer = CrossRecipePatternUpload.isBuffer(host);
        Request request = request(context, crossRecipeBuffer);
        // null: the buffer's pool could not be read; "": no pool selected.
        String terminalMap = crossRecipeBuffer ? CrossRecipePatternUpload.terminalRecipeMap(host) : "";
        Pool pool = new Pool(
            terminalMap == null,
            terminalMap == null ? "" : terminalMap,
            terminalMap == null ? null : CrossRecipePatternUpload.resolveMap(terminalMap));
        String configuredMap = pool.id;
        MatchResult best = null;
        MatchResult bestProgrammable = null;
        MatchResult resolved = null;
        RecipeMap<?> resolvedMap = null;
        boolean ambiguous = false;
        for (Route route : routes) {
            RecipeMap<?> current = route.map();
            // A GTNR pattern tag selects the map; otherwise the pool chosen in the buffer's terminal does. The
            // controller's current mode is only the fallback, so buffers bound to different pools on one controller
            // are matched (and grouped) against their own pools.
            RecipeMap<?> requested = request.tagged ? request.sourceMap : pool.map;
            MapSupport mapSupport = crossRecipeBuffer && requested != null
                && route.receiver() instanceof MTEMultiBlockBase controller
                    ? CrossRecipePatternUpload.mapSupport(controller, current, requested)
                    : null;
            boolean crossRecipe = mapSupport == MapSupport.VERIFIED && requested != current;
            if (crossRecipeBuffer && current != null) route = routeWithMap(route, crossRecipe ? requested : current);
            boolean canProgram = canProgram(route, programmable);
            Decision decision = evaluate(route, request, canProgram, crossRecipeBuffer, pool, mapSupport);
            Level level = decision.level == Level.ROUTED && crossRecipeBuffer
                ? crossRecipe ? Level.GTNR_CROSS_RECIPE : Level.GT_NATIVE
                : decision.level;
            long voltage = GtRouteDiscovery.inputVoltage(route.receiver());
            MatchResult candidate = new MatchResult(
                level,
                decision.reason,
                decision.circuit,
                route.name(),
                route.icon(),
                signature(route, decision.circuit),
                canProgram,
                request.categoryId(route, crossRecipeBuffer),
                request.selectionKey(),
                voltage > 0 ? GTUtility.getColoredTierNameFromVoltage(voltage) : "",
                route.circuit(),
                voltage > 0 ? GTUtility.getTier(voltage) : -1,
                configuredMap);
            if (candidate.allowsMapping()) {
                if (resolved == null || candidate.level.rank() < resolved.level.rank()) {
                    resolved = candidate;
                    resolvedMap = route.map();
                    ambiguous = false;
                } else if (candidate.level.rank() == resolved.level.rank()
                    && (resolvedMap != route.map() || !resolved.sameRecipeSelection(candidate))) ambiguous = true;
            }
            if (best == null || candidate.level.rank() < best.level.rank()
                || candidate.level.rank() == best.level.rank() && candidate.priorityScore(0) > best.priorityScore(0))
                best = candidate;
            if (candidate.programmingCover && candidate.allowsAuto()
                && (bestProgrammable == null || candidate.priorityScore(0) > bestProgrammable.priorityScore(0)))
                bestProgrammable = candidate;
        }
        if (!ambiguous) return best;
        if (request.source == null && bestProgrammable != null) return bestProgrammable;
        return new MatchResult(
            Level.UNVERIFIED,
            reasonKey("route_ambiguous"),
            best.circuit,
            best.routeName,
            best.routeIcon,
            best.routeSignature,
            best.programmingCover,
            best.categoryId,
            best.selectionKey,
            best.voltageTier,
            best.configuredCircuit,
            best.voltageTierRank,
            best.configuredRecipeMap);
    }

    private static boolean rejectsFluidInputs(Object host, PatternContext context) {
        if (!(host instanceof IDualInputHatch hatch) || hatch.supportsFluids() || context.recipeInputs == null)
            return false;
        for (IAEStack<?> input : context.recipeInputs) {
            if (input instanceof IAEFluidStack && input.getStackSize() > 0) return true;
        }
        return false;
    }

    private static Request request(PatternContext context, boolean crossRecipeBuffer) {
        RouteHint source = context.hint != null && !context.hint.isEmpty() ? context.hint : null;
        String patternMap = CrossRecipePatternUpload.patternRecipeMap(context.pattern);
        boolean hintHasMap = source != null && source.hasRecipeMap();
        String sourceMapId = patternMap != null ? patternMap : hintHasMap ? source.getRecipeMapId() : "";
        boolean hasRecipeMap = patternMap != null || hintHasMap;
        RecipeMap<?> sourceMap = patternMap != null ? RecipeMap.ALL_RECIPE_MAPS.get(patternMap)
            : hintHasMap ? knownMap(sourceMapId) : null;
        IAEStack<?>[] inputs = context.recipeInputs;
        ItemStack patternCircuit = GtCircuits.patternCircuit(inputs);
        boolean circuitConflict = GtCircuits.conflictingCircuits(inputs);
        ItemStack virtualCircuit = null;
        if (crossRecipeBuffer) {
            int stored = CrossRecipePatternUpload.storedCircuit(context.pattern);
            if (stored >= 0) {
                virtualCircuit = GTUtility.getIntegratedCircuit(stored);
                circuitConflict |= GtCircuits.resetsSelector(inputs)
                    || patternCircuit != null && !GtCircuits.same(patternCircuit, virtualCircuit);
                patternCircuit = virtualCircuit;
            } else if (stored == -2) circuitConflict = true;
        }
        return new Request(
            context,
            source,
            sourceMapId,
            sourceMap,
            hasRecipeMap,
            patternMap != null,
            patternCircuit,
            virtualCircuit,
            selectedCircuit(context, patternCircuit),
            GtCircuits.hasProgrammingCircuit(inputs),
            circuitConflict);
    }

    private Decision evaluate(Route route, Request request, boolean canProgram, boolean crossRecipeBuffer, Pool pool,
        MapSupport mapSupport) {
        ItemStack selected = request.selectedCircuit;
        if (request.circuitConflict) return new Decision(Level.CIRCUIT_CONFLICT, "circuits_multiple", selected);
        Decision rejected = checkDestination(route, request, crossRecipeBuffer, mapSupport);
        if (rejected != null) return rejected;
        if (crossRecipeBuffer && route.receiver() instanceof MTEMultiBlockBase controller) {
            // An unreadable pool is unknown, never "unrestricted": the buffer could reject or misroute the pattern.
            if (pool.unreadable) return new Decision(Level.UNVERIFIED, "gtnr_pool_unreadable", selected);
            if (!pool.id.isEmpty() && pool.map == null)
                return new Decision(Level.UNVERIFIED, "gtnr_pool_unknown", selected);
            if (pool.map != null && pool.map != route.map())
                return new Decision(Level.INCOMPATIBLE, "gtnr_pool_mismatch", selected);
            var lock = controller.isRecipeLockingEnabled() ? controller.getSingleRecipeCheck() : null;
            if (lock != null && lock.getRecipeMap() != route.map())
                return new Decision(Level.INCOMPATIBLE, "gtnr_switch_unavailable", selected);
            var cache = request.virtualCircuit == null ? verdicts : virtualCircuitVerdicts;
            var inference = lock == null ? cache.infer(route.map(), request.context, request.virtualCircuit)
                : cache.inferLocked(route.map(), request.context, request.virtualCircuit, lock.getRecipe());
            Decision match = inferSelection(route, request, canProgram, inference);
            if (lock != null || match.level != Level.WRONG_RECIPE
                || request.tagged
                || request.sourceMap == null
                || request.sourceMap == route.map()) return match;
            // The NEI source names another map. A selected pool fixes the map, so this buffer cannot run it.
            if (pool.map != null) return new Decision(Level.INCOMPATIBLE, "gtnr_pool_mismatch", selected);
            if (CrossRecipePatternUpload.mapSupport(controller, route.map(), request.sourceMap)
                != MapSupport.UNAVAILABLE) return new Decision(Level.UNVERIFIED, "gtnr_missing_map", selected);
            return match;
        }
        return request.hasRecipeMap || request.source != null ? checkCircuit(route, request, selected, canProgram)
            : inferSelection(route, request, canProgram, verdicts.infer(route.map(), request.context));
    }

    private static Route routeWithMap(Route route, RecipeMap<?> map) {
        String name = route.name();
        int mode = name == null ? -1 : name.indexOf(MODE_SEPARATOR);
        if (mode >= 0) name = name.substring(0, mode);
        if (name != null) name += MODE_SEPARATOR + map.unlocalizedName;
        return new Route(
            map,
            route.circuit(),
            route.icon(),
            name,
            route.receiver(),
            route.programmingCover(),
            route.programmable());
    }

    private static Decision checkDestination(Route route, Request request, boolean crossRecipeBuffer,
        MapSupport mapSupport) {
        ItemStack selected = request.selectedCircuit;
        if (route.map() == null) return new Decision(Level.UNVERIFIED, "no_controller", selected);

        RouteHint source = request.source;
        if (crossRecipeBuffer) {
            if (request.tagged) {
                if (request.sourceMap == null) return new Decision(Level.INCOMPATIBLE, "gtnr_invalid_map", selected);
                if (mapSupport == MapSupport.UNAVAILABLE)
                    return new Decision(Level.INCOMPATIBLE, "gtnr_switch_unavailable", selected);
                if (mapSupport != MapSupport.VERIFIED)
                    return new Decision(Level.UNVERIFIED, "gtnr_switch_unverified", selected);
            }
        } else if (request.hasRecipeMap) {
            if (request.sourceMap == null) return new Decision(Level.UNVERIFIED, "source_map_unknown", selected);
            if (request.sourceMap != route.map()) return new Decision(Level.WRONG_RECIPE, "hint_map", selected);
        } else if (source != null && source.hasMachineTypes()
            && !source.matchesMachine(route.icon())
            && (route.receiver() == null || !source.matchesMachine(
                route.receiver()
                    .getStackForm(1)))) {
                        return new Decision(Level.WRONG_RECIPE, "hint_map", selected);
                    }
        if (route.receiver() == null) return new Decision(Level.UNVERIFIED, "no_controller", selected);
        if (route.map() == TecTechRecipeMaps.condensateAssemblingRecipes && !(route.receiver() instanceof MTEBECIONode))
            return new Decision(Level.INCOMPATIBLE, "bec_role", selected);
        return null;
    }

    private static Decision inferSelection(Route route, Request request, boolean canProgram,
        GtRecipeVerifier.Inference inferred) {
        if (!inferred.complete) return new Decision(Level.UNVERIFIED, "search_incomplete", request.selectedCircuit);
        if (!inferred.found()) return new Decision(Level.WRONG_RECIPE, "recipe", request.selectedCircuit);
        if (!inferred.unique()) {
            ItemStack installed = route.circuit();
            if (request.programmingCircuit && !canProgram
                && !GtCircuits.programmingCircuitsMatch(request.context.recipeInputs, installed))
                return new Decision(Level.CIRCUIT_CONFLICT, "needs_programming", installed);
            if (!inferred.allows(installed)) return new Decision(
                Level.CIRCUIT_CONFLICT,
                installed == null ? "machine_circuit_missing" : "machine_circuit",
                installed);
            return new Decision(Level.UNVERIFIED, "circuit_unknown", installed);
        }
        ItemStack selected = request.patternCircuit != null ? request.patternCircuit : inferred.circuit();
        return checkCircuit(route, request, selected, canProgram);
    }

    private static Decision checkCircuit(Route route, Request request, ItemStack selected, boolean canProgram) {
        ItemStack installed = canProgram ? null : route.circuit();
        if (request.programmingCircuit && !canProgram
            && !GtCircuits.programmingCircuitsMatch(request.context.recipeInputs, installed))
            return new Decision(Level.CIRCUIT_CONFLICT, "needs_programming", selected);

        boolean sourceWithoutMap = request.source != null && !request.hasRecipeMap;
        if (selected != null) {
            if (installed != null && !GtCircuits.same(selected, installed))
                return new Decision(Level.CIRCUIT_CONFLICT, "machine_circuit", selected);
            if (request.patternCircuit == null && (installed == null || request.programmingCircuit))
                return new Decision(Level.CIRCUIT_CONFLICT, "machine_circuit_missing", selected);
        } else if (sourceWithoutMap && installed != null) {
            return new Decision(Level.UNVERIFIED, "circuit_unknown", installed);
        }
        if (sourceWithoutMap && !request.source.hasMachineTypes())
            return new Decision(Level.UNVERIFIED, "no_route", selected);
        return new Decision(Level.ROUTED, null, selected);
    }

    private static boolean canProgram(Route route, boolean programmable) {
        return programmable || route.programmingCover() || route.programmable();
    }

    private static String signature(Route route, ItemStack circuit) {
        String map = route.map() == null ? "" : route.map().unlocalizedName;
        String role = route.receiver() == null ? ""
            : route.receiver()
                .getClass()
                .getName();
        return map + "#" + GtCircuits.number(circuit) + "#" + role;
    }

    private static String reasonKey(String reason) {
        return "gui.neoecoae.pattern_upload.reason." + reason;
    }

    private static RecipeMap<?> knownMap(String id) {
        // NEI sources report either the unlocalized name or GT's old identifier.
        return CrossRecipePatternUpload.resolveMap(id);
    }

    /**
     * The recipe pool chosen in a GTNR buffer's terminal. {@code unreadable} means the buffer exposes a pool that could
     * not be read; an empty {@code id} means none is selected; {@code map} is null when the id names no loaded map.
     */
    @com.github.bsideup.jabel.Desugar
    private record Pool(boolean unreadable, String id, RecipeMap<?> map) {}

    @com.github.bsideup.jabel.Desugar
    private record Request(PatternContext context, RouteHint source, String sourceMapId, RecipeMap<?> sourceMap,
        boolean hasRecipeMap, boolean tagged, ItemStack patternCircuit, ItemStack virtualCircuit,
        ItemStack selectedCircuit, boolean programmingCircuit, boolean circuitConflict) {

        String categoryId(Route route, boolean crossRecipeBuffer) {
            if (crossRecipeBuffer && route.map() != sourceMap)
                return route.map() == null ? "" : "gt:" + route.map().unlocalizedName;
            if (source != null && !source.getCategoryId()
                .isEmpty() && (!hasRecipeMap || sourceMapId.equals(source.getRecipeMapId())))
                return source.getCategoryId();
            if (hasRecipeMap && sourceMap != null) return "gt:" + sourceMap.unlocalizedName;
            return route.map() == null ? "" : "gt:" + route.map().unlocalizedName;
        }

        String selectionKey() {
            return circuitConflict ? "!" : GtPatternRoutes.selectionKey(context, selectedCircuit);
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record Decision(Level level, String reason, ItemStack circuit) {

        private Decision {
            if (reason != null) reason = reasonKey(reason);
        }
    }
}
