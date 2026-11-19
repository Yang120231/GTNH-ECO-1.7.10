package cn.dancingsnow.neoecoae.platform.gt.upload;

import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.compat.gtnr.upload.CrossRecipePatternUpload;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult;
import cn.dancingsnow.neoecoae.crafting.upload.MatchResult.Level;
import cn.dancingsnow.neoecoae.crafting.upload.PatternContext;
import cn.dancingsnow.neoecoae.crafting.upload.PatternRouteProbe;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.crafting.upload.RouteHint;
import cn.dancingsnow.neoecoae.platform.gt.upload.GtRouteDiscovery.Route;
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

        Request request = request(context);
        MatchResult best = null;
        MatchResult bestProgrammable = null;
        MatchResult resolved = null;
        RecipeMap<?> resolvedMap = null;
        boolean ambiguous = false;
        for (Route route : routes) {
            route = routeForPatternMap(host, route, request);
            boolean canProgram = canProgram(route, programmable);
            Decision decision = evaluate(host, route, request, canProgram);
            long voltage = GtRouteDiscovery.inputVoltage(route.receiver());
            MatchResult candidate = new MatchResult(
                decision.level,
                decision.reason,
                decision.circuit,
                route.name(),
                route.icon(),
                signature(route, decision.circuit),
                canProgram,
                request.categoryId(route),
                request.selectionKey(),
                voltage > 0 ? GTUtility.getColoredTierNameFromVoltage(voltage) : "",
                route.circuit(),
                voltage > 0 ? GTUtility.getTier(voltage) : -1);
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
            best.voltageTierRank);
    }

    private static boolean rejectsFluidInputs(Object host, PatternContext context) {
        if (!(host instanceof IDualInputHatch hatch) || hatch.supportsFluids() || context.recipeInputs == null)
            return false;
        for (IAEStack<?> input : context.recipeInputs) {
            if (input instanceof IAEFluidStack && input.getStackSize() > 0) return true;
        }
        return false;
    }

    private static Request request(PatternContext context) {
        RouteHint source = context.hint != null && !context.hint.isEmpty() ? context.hint : null;
        String patternMap = CrossRecipePatternUpload.patternRecipeMap(context.pattern);
        boolean hintHasMap = source != null && source.hasRecipeMap();
        String sourceMapId = patternMap != null ? patternMap : hintHasMap ? source.getRecipeMapId() : "";
        boolean hasRecipeMap = patternMap != null || hintHasMap;
        RecipeMap<?> sourceMap = hasRecipeMap ? knownMap(sourceMapId) : null;
        IAEStack<?>[] inputs = context.recipeInputs;
        ItemStack patternCircuit = GtCircuits.patternCircuit(inputs);
        return new Request(
            context,
            source,
            sourceMapId,
            sourceMap,
            hasRecipeMap,
            patternCircuit,
            selectedCircuit(context, patternCircuit),
            GtCircuits.hasProgrammingCircuit(inputs),
            GtCircuits.conflictingCircuits(inputs));
    }

    private Decision evaluate(Object host, Route route, Request request, boolean canProgram) {
        ItemStack selected = request.selectedCircuit;
        if (request.circuitConflict) return new Decision(Level.CIRCUIT_CONFLICT, "circuits_multiple", selected);

        Decision rejected = checkDestination(host, route, request);
        if (rejected != null) return rejected;
        return request.hasRecipeMap || request.source != null ? checkCircuit(route, request, selected, canProgram)
            : inferSelection(route, request, canProgram);
    }

    private static Route routeForPatternMap(Object host, Route route, Request request) {
        if (!CrossRecipePatternUpload.isBuffer(host) || !request.hasRecipeMap
            || request.sourceMap == null
            || !CrossRecipePatternUpload.selectsSupportedMap(host, request.sourceMapId)
            || route.map() == request.sourceMap) return route;
        if (!CrossRecipePatternUpload.controllerSupportsMap(route.receiver(), request.sourceMapId)) return route;
        String name = route.name();
        int mode = name == null ? -1 : name.indexOf(MODE_SEPARATOR);
        if (mode >= 0) name = name.substring(0, mode);
        if (name != null) name += MODE_SEPARATOR + request.sourceMap.unlocalizedName;
        return new Route(
            request.sourceMap,
            route.circuit(),
            route.icon(),
            name,
            route.receiver(),
            route.programmingCover(),
            route.programmable());
    }

    private static Decision checkDestination(Object host, Route route, Request request) {
        ItemStack selected = request.selectedCircuit;
        if (route.map() == null) return new Decision(Level.UNVERIFIED, "no_controller", selected);

        RouteHint source = request.source;
        if (request.hasRecipeMap) {
            // An invalid captured source is not permission to infer a different map from the edited pattern.
            if (request.sourceMap == null) return new Decision(Level.UNVERIFIED, "source_map_unknown", selected);
            boolean crossRecipeBuffer = CrossRecipePatternUpload.isBuffer(host);
            if ((crossRecipeBuffer && !CrossRecipePatternUpload.selectsSupportedMap(host, request.sourceMapId))
                || (crossRecipeBuffer && request.sourceMap != route.map()
                    && !CrossRecipePatternUpload.controllerSupportsMap(route.receiver(), request.sourceMapId))
                || (!crossRecipeBuffer && request.sourceMap != route.map()))
                return new Decision(Level.WRONG_RECIPE, "hint_map", selected);
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

    private Decision inferSelection(Route route, Request request, boolean canProgram) {
        GtRecipeVerifier.Inference inferred = verdicts.infer(route.map(), request.context);
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
        try {
            return RecipeMap.getFromOldIdentifier(id);
        } catch (RuntimeException unknown) {
            return null;
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record Request(PatternContext context, RouteHint source, String sourceMapId, RecipeMap<?> sourceMap,
        boolean hasRecipeMap, ItemStack patternCircuit, ItemStack selectedCircuit, boolean programmingCircuit,
        boolean circuitConflict) {

        String categoryId(Route route) {
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
