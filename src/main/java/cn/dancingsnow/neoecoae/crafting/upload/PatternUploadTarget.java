package cn.dancingsnow.neoecoae.crafting.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.crafting.ICraftingIconProvider;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IInterfaceViewable;
import appeng.helpers.IInterfaceHost;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.common.items.ItemIntegratedCircuit;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

/** A server-owned, short-lived upload destination. */
public final class PatternUploadTarget {

    public enum Kind {
        AE2_INTERFACE,
        GT_CRAFTING_INPUT,
        ECO_PATTERN_BUS,
        PROGRAMMABLE_HATCH,
        GT_CRAFTING_INPUT_BUS,
        AE2_DUAL_INTERFACE
    }

    private static final class Route {

        private final RecipeMap<?> recipeMap;
        private final ItemStack circuit;
        private final ForgeDirection side;
        private final ItemStack icon;
        private final String name;

        private Route(RecipeMap<?> recipeMap, ItemStack circuit, ForgeDirection side) {
            this(recipeMap, circuit, side, null, null);
        }

        private Route(RecipeMap<?> recipeMap, ItemStack circuit, ForgeDirection side, ItemStack icon, String name) {
            this.recipeMap = recipeMap;
            this.circuit = circuit == null ? null : circuit.copy();
            this.side = side == null ? ForgeDirection.UNKNOWN : side;
            this.icon = icon == null ? null : icon.copy();
            this.name = name;
        }
    }

    private final String id;
    private final Kind kind;
    private final String name;
    private final ItemStack icon;
    private final ItemStack routingIcon;
    private final ItemStack circuit;
    private final RecipeMap<?> recipeMap;
    private final List<Route> routes;
    private final IInventory inventory;
    private final int slotCount;
    private final int x;
    private final int y;
    private final int z;
    private final int dimension;
    private final IGrid grid;
    private final Object host;

    private PatternUploadTarget(String id, Kind kind, String name, ItemStack icon, ItemStack routingIcon,
        List<Route> routes, IInventory inventory, int slotCount, int x, int y, int z, int dimension, IGrid grid,
        Object host) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.icon = icon == null ? null : icon.copy();
        this.routingIcon = routingIcon == null ? null : routingIcon.copy();
        this.routes = routes == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(routes));
        this.recipeMap = this.routes.isEmpty() ? null : this.routes.get(0).recipeMap;
        this.circuit = this.routes.isEmpty() ? null
            : this.routes.get(0).circuit == null ? null : this.routes.get(0).circuit.copy();
        this.inventory = inventory;
        this.slotCount = Math.max(0, Math.min(slotCount, inventory == null ? 0 : inventory.getSizeInventory()));
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.grid = grid;
        this.host = host;
    }

    public static PatternUploadTarget interfaceTarget(String id, IInterfaceViewable viewable, IGrid grid) {
        TileEntity tile = viewable.getTileEntity();
        int dimension = tile == null || tile.getWorldObj() == null ? 0 : tile.getWorldObj().provider.dimensionId;
        ItemStack routingIcon = viewable.getDisplayRep();
        ItemStack icon = routingIcon;
        if (icon == null) {
            icon = viewable.getSelfRep();
        }
        Kind kind = interfaceKind(viewable);
        List<Route> routes = interfaceRoutes(viewable, routingIcon);
        return new PatternUploadTarget(
            id,
            kind,
            viewable.getName(),
            icon,
            routingIcon,
            routes,
            viewable.getPatterns(),
            viewable.numSlots(),
            tile == null ? 0 : tile.xCoord,
            tile == null ? 0 : tile.yCoord,
            tile == null ? 0 : tile.zCoord,
            dimension,
            grid,
            viewable);
    }

    public static PatternUploadTarget ecoTarget(String id, TileCraftingPatternBus bus, TileECOInterface owner,
        IGrid grid) {
        int dimension = bus.getWorldObj() == null ? 0 : bus.getWorldObj().provider.dimensionId;
        ItemStack busIcon = bus.getBlockType() == null ? null
            : new ItemStack(bus.getBlockType(), 1, bus.getBlockMetadata());
        return new PatternUploadTarget(
            id,
            Kind.ECO_PATTERN_BUS,
            bus.getInventoryName(),
            busIcon,
            null,
            Collections.emptyList(),
            bus,
            bus.getSizeInventory(),
            bus.xCoord,
            bus.yCoord,
            bus.zCoord,
            dimension,
            grid,
            owner);
    }

    public static PatternUploadTarget providerTarget(String id, IPatternUploadProvider provider, IGrid grid) {
        TileEntity tile = provider.getTileEntity();
        int dimension = tile == null || tile.getWorldObj() == null ? 0 : tile.getWorldObj().provider.dimensionId;
        IInventory inventory = provider.getPatternUploadInventory();
        return new PatternUploadTarget(
            id,
            Kind.PROGRAMMABLE_HATCH,
            provider.getPatternUploadName(),
            provider.getPatternUploadIcon(),
            provider.getPatternUploadIcon(),
            Collections.singletonList(
                new Route(
                    provider.getPatternUploadRecipeMap(),
                    null,
                    ForgeDirection.UNKNOWN,
                    provider.getPatternUploadIcon(),
                    provider.getPatternUploadName())),
            inventory,
            inventory == null ? 0 : inventory.getSizeInventory(),
            tile == null ? 0 : tile.xCoord,
            tile == null ? 0 : tile.yCoord,
            tile == null ? 0 : tile.zCoord,
            dimension,
            grid,
            provider);
    }

    public String getId() {
        return this.id;
    }

    public Kind getKind() {
        return this.kind;
    }

    public String getName() {
        return this.name == null ? "" : this.name;
    }

    public String getTooltipName() {
        return this.getTooltipName(null, null);
    }

    public String getTooltipName(PatternRouteKey routeKey, ICraftingPatternDetails details) {
        ItemStack selectedIcon = this.getDisplayIcon(routeKey, details);
        String selectedName = this.getDisplayName(routeKey, details);
        if (selectedName != null && selectedName.indexOf('.') < 0) return selectedName;
        if (selectedIcon != null) {
            String displayName = selectedIcon.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) return displayName;
        }
        if (selectedName != null && !selectedName.isEmpty()) return selectedName;
        String name = this.getName();
        if (name.indexOf('.') < 0 || this.icon == null) return name;
        String displayName = this.icon.getDisplayName();
        return displayName == null || displayName.isEmpty() ? name : displayName;
    }

    public ItemStack getIcon() {
        return this.icon == null ? null : this.icon.copy();
    }

    public ItemStack getDisplayIcon(PatternRouteKey routeKey, ICraftingPatternDetails details) {
        Route route = this.selectRoute(routeKey, details);
        if (route != null && route.icon != null) return route.icon.copy();
        return this.getIcon();
    }

    public String getDisplayName(PatternRouteKey routeKey, ICraftingPatternDetails details) {
        Route route = this.selectRoute(routeKey, details);
        return route == null || route.name == null || route.name.isEmpty() ? this.getName() : route.name;
    }

    public ItemStack getCircuit() {
        return this.circuit == null ? null : this.circuit.copy();
    }

    /**
     * Returns the circuit belonging to the route selected by the encoded pattern.
     *
     * <p>
     * An AE2 interface can have more than one configured target side. The first route is
     * only a stable fallback for legacy callers; upload UI and tooltips should use this overload
     * so a route connected to another machine cannot leak its circuit number.
     * </p>
     */
    public ItemStack getCircuit(ICraftingPatternDetails details) {
        return this.getCircuit(null, details);
    }

    /** Returns the circuit for the best route, optionally constrained by a captured RecipeMap. */
    public ItemStack getCircuit(PatternRouteKey routeKey, ICraftingPatternDetails details) {
        Route route = this.selectRoute(routeKey, details);
        if (route == null || route.circuit == null) return null;
        return route.circuit.copy();
    }

    /** Returns the RecipeMap selected for the encoded pattern, if this target exposes one. */
    public RecipeMap<?> getRecipeMap(ICraftingPatternDetails details) {
        return this.getRecipeMap(null, details);
    }

    /** Returns the RecipeMap for the best route, optionally constrained by a captured RecipeMap. */
    public RecipeMap<?> getRecipeMap(PatternRouteKey routeKey, ICraftingPatternDetails details) {
        Route route = this.selectRoute(routeKey, details);
        return route == null ? null : route.recipeMap;
    }

    public int getSlotCount() {
        return this.slotCount;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getDimension() {
        return this.dimension;
    }

    public IGrid getGrid() {
        return this.grid;
    }

    public Object getHost() {
        return this.host;
    }

    public boolean isCompatible(boolean processing, ICraftingPatternDetails details) {
        return this.isCompatible(processing, details, null);
    }

    public boolean isCompatible(boolean processing, ICraftingPatternDetails details, PatternRouteKey routeKey) {
        return this.isRecipeCompatible(processing, details, routeKey);
    }

    /** A recipe-compatible target remains visible even when its circuit is not an exact match. */
    public boolean isRecipeCompatible(boolean processing, ICraftingPatternDetails details, PatternRouteKey routeKey) {
        if (details == null) return true;
        // Rank 1 means "this target knows a different RecipeMap". It is useful for internal
        // diagnostics but must stay out of the upload list; only rank 0 is the requested map.
        return this.recipeTypeRank(processing, details, routeKey) == 0;
    }

    public boolean isWritable(boolean processing, ICraftingPatternDetails details, ItemStack pattern) {
        return this.isWritable(processing, details, pattern, null);
    }

    public boolean isWritable(boolean processing, ICraftingPatternDetails details, ItemStack pattern,
        PatternRouteKey routeKey) {
        return pattern != null && details != null
            && this.isPresent()
            && this.isExactMatch(processing, details, routeKey)
            && this.firstEmptySlot(pattern) >= 0;
    }

    public boolean isPresent() {
        if (!this.isStillOnGrid()) return false;
        TileEntity tile = this.host instanceof IInterfaceViewable ? ((IInterfaceViewable) this.host).getTileEntity()
            : this.host instanceof IPatternUploadProvider ? ((IPatternUploadProvider) this.host).getTileEntity()
                : this.inventory instanceof TileEntity ? (TileEntity) this.inventory : null;
        return tile != null && !tile.isInvalid()
            && tile.getWorldObj() != null
            && tile.getWorldObj()
                .getTileEntity(this.x, this.y, this.z) == tile;
    }

    public int compatibilityRank(boolean processing, ICraftingPatternDetails details) {
        return this.compatibilityRank(processing, details, null);
    }

    public int compatibilityRank(boolean processing, ICraftingPatternDetails details, PatternRouteKey routeKey) {
        if (details == null) return 0;
        int baseRank = this.recipeTypeRank(processing, details, routeKey);
        if (baseRank < 0) return -1;
        ItemStack patternCircuit = patternCircuit(details);
        ItemStack requiredCircuit = patternCircuit == null || !routeKeyHasCircuit(routeKey) ? patternCircuit
            : routeKey.getCircuit();
        if (requiredCircuit == null && routeKey != null) requiredCircuit = routeKey.getCircuit();
        if (requiredCircuit == null) {
            return baseRank * 10 + (this.hasKnownCircuit() && processing ? 1 : 0);
        }
        int circuitRank = this.circuitRank(details, requiredCircuit, routeKey);
        return baseRank * 10 + circuitRank;
    }

    public boolean hasMatchingRecipeType(boolean processing, ICraftingPatternDetails details) {
        return this.recipeTypeRank(processing, details) == 0;
    }

    public boolean hasMatchingCircuitNumber(ICraftingPatternDetails details) {
        return this.hasMatchingCircuitNumber(details, null);
    }

    public boolean hasMatchingCircuitNumber(ICraftingPatternDetails details, PatternRouteKey routeKey) {
        ItemStack patternCircuit = patternCircuit(details);
        if (patternCircuit != null && routeKey != null
            && routeKey.hasCircuit()
            && !routeKey.matchesCircuit(patternCircuit)) return false;
        ItemStack requiredCircuit = patternCircuit != null ? patternCircuit
            : routeKey == null ? null : routeKey.getCircuit();
        if (requiredCircuit == null) {
            for (Route route : this.routes) {
                if (route.recipeMap == null) continue;
                if (route.circuit == null && this.routeMatchesRecipe(route, details, routeKey)) return true;
                if (route.circuit != null && this.routeMatchesRecipeForExact(route, details, routeKey)) return true;
            }
            return this.kind == Kind.ECO_PATTERN_BUS || this.kind == Kind.PROGRAMMABLE_HATCH;
        }
        for (Route route : this.routes) {
            if (route.recipeMap != null && this.routeMatchesRecipe(route, details, routeKey)
                && PatternCircuitCompat.same(route.circuit, requiredCircuit)) return true;
        }
        return this.kind == Kind.ECO_PATTERN_BUS || this.acceptsProgrammableCircuit(details);
    }

    public boolean isExactMatch(boolean processing, ICraftingPatternDetails details) {
        if (details == null) return false;
        return this.hasMatchingRecipeType(processing, details) && this.hasMatchingCircuitNumber(details);
    }

    public boolean isExactMatch(boolean processing, ICraftingPatternDetails details, PatternRouteKey routeKey) {
        if (details == null) return false;
        if (routeKey == null || routeKey.isEmpty()) return this.isExactMatch(processing, details);
        if (!processing) return this.isExactMatch(processing, details);

        if (this.recipeTypeRank(processing, details, routeKey) < 0) return false;
        // The RecipeMap captured from NEI is authoritative, but the recipe shape is still checked
        // with the captured virtual circuit when AE2 omitted that non-consumable input.
        ItemStack patternCircuit = patternCircuit(details);
        if (patternCircuit != null && routeKey.hasCircuit() && !routeKey.matchesCircuit(patternCircuit)) return false;
        ItemStack requiredCircuit = patternCircuit != null ? patternCircuit : routeKey.getCircuit();
        if (requiredCircuit == null) {
            for (Route route : this.routes) {
                if (route.recipeMap != null && routeKey.matches(route.recipeMap.unlocalizedName)
                    && this.routeMatchesRecipeForExact(route, details, routeKey)) return true;
            }
            return this.kind == Kind.ECO_PATTERN_BUS || this.acceptsProgrammableCircuit(details);
        }
        for (Route route : this.routes) {
            if (route.recipeMap == null || !routeKey.matches(route.recipeMap.unlocalizedName)) continue;
            if (this.routeMatchesRecipe(route, details, routeKey)
                && PatternCircuitCompat.same(route.circuit, requiredCircuit)) return true;
        }
        return this.kind == Kind.ECO_PATTERN_BUS || this.acceptsProgrammableCircuit(details);
    }

    public boolean matchesRouteKey(PatternRouteKey routeKey, ICraftingPatternDetails details) {
        if (routeKey == null || routeKey.isEmpty()) return true;
        for (Route route : this.routes) {
            if (route.recipeMap != null && routeKey.matches(route.recipeMap.unlocalizedName)) return true;
        }
        return false;
    }

    private int recipeTypeRank(boolean processing, ICraftingPatternDetails details) {
        return this.recipeTypeRank(processing, details, null);
    }

    private int recipeTypeRank(boolean processing, ICraftingPatternDetails details, PatternRouteKey routeKey) {
        int baseRank;
        if (this.kind == Kind.ECO_PATTERN_BUS) baseRank = processing ? 2 : 0;
        else {
            if (this.kind == Kind.PROGRAMMABLE_HATCH && !this.isStillOnGrid()) return -1;
            if (processing) {
                if (this.hasMatchingRecipeMap(details, routeKey)) baseRank = 0;
                else if (this.hasKnownRecipeMap()) baseRank = 1;
                else baseRank = -1;
            } else {
                ItemStack display = this.routingIcon;
                if (display == null) return -1;
                ItemStack molecular = AEApi.instance()
                    .definitions()
                    .blocks()
                    .molecularAssembler()
                    .maybeStack(1)
                    .orNull();
                baseRank = molecular != null && display.isItemEqual(molecular) ? 0 : -1;
            }
        }
        return baseRank;
    }

    private boolean hasKnownRecipeMap() {
        for (Route route : this.routes) if (route.recipeMap != null) return true;
        return false;
    }

    private boolean hasMatchingRecipeMap(ICraftingPatternDetails details) {
        return this.hasMatchingRecipeMap(details, null);
    }

    private boolean hasMatchingRecipeMap(ICraftingPatternDetails details, PatternRouteKey routeKey) {
        for (Route route : this.routes) {
            if (route.recipeMap == null) continue;
            if (routeKey != null && !routeKey.isEmpty() && !routeKey.matches(route.recipeMap.unlocalizedName)) continue;
            if (this.routeMatchesRecipe(route, details, routeKey)) return true;
        }
        return false;
    }

    private int circuitRank(ICraftingPatternDetails details, ItemStack requiredCircuit) {
        return this.circuitRank(details, requiredCircuit, null);
    }

    private int circuitRank(ICraftingPatternDetails details, ItemStack requiredCircuit, PatternRouteKey routeKey) {
        boolean unknown = false;
        boolean knownMap = false;
        for (Route route : this.routes) {
            if (route.recipeMap == null || !this.routeMatchesRecipe(route, details, routeKey)) continue;
            knownMap = true;
            if (PatternCircuitCompat.same(route.circuit, requiredCircuit)) return 0;
            if (route.circuit == null) unknown = true;
        }
        if (unknown || !knownMap) return 1;
        return 2;
    }

    private boolean routeMatchesRecipe(Route route, ICraftingPatternDetails details, PatternRouteKey routeKey) {
        if (route == null || route.recipeMap == null) return false;
        ItemStack patternCircuit = patternCircuit(details);
        if (patternCircuit != null) return PatternRecipeMatcher.matches(route.recipeMap, details, null);
        if (routeKey != null && routeKey.hasCircuit()) {
            return PatternRecipeMatcher.matches(route.recipeMap, details, routeKey.getCircuit());
        }
        return PatternRecipeMatcher.matchesAnyCircuit(route.recipeMap, details, route.circuit);
    }

    /** Exact route check used when NEI did not provide a route key. */
    private boolean routeMatchesRecipeForExact(Route route, ICraftingPatternDetails details, PatternRouteKey routeKey) {
        if (route == null || route.recipeMap == null) return false;
        ItemStack patternCircuit = patternCircuit(details);
        if (patternCircuit != null) return PatternRecipeMatcher.matches(route.recipeMap, details, null);
        ItemStack contextualCircuit = routeKey != null && routeKey.hasCircuit() ? routeKey.getCircuit() : route.circuit;
        return PatternRecipeMatcher.matches(route.recipeMap, details, contextualCircuit);
    }

    private boolean hasKnownCircuit() {
        for (Route route : this.routes) if (route.circuit != null) return true;
        return false;
    }

    private boolean acceptsProgrammableCircuit(ICraftingPatternDetails details) {
        return this.kind == Kind.PROGRAMMABLE_HATCH && hasProgrammableCircuit(details);
    }

    private static boolean hasProgrammableCircuit(ICraftingPatternDetails details) {
        if (details == null || details.getCondensedAEInputs() == null) return false;
        for (IAEStack<?> input : details.getCondensedAEInputs()) {
            if (!(input instanceof IAEItemStack)) continue;
            if (PatternCircuitCompat.isProgrammingCircuit(((IAEItemStack) input).getItemStack())) return true;
        }
        return false;
    }

    private static boolean routeKeyHasCircuit(PatternRouteKey routeKey) {
        return routeKey != null && routeKey.hasCircuit() && routeKey.getCircuit() != null;
    }

    /**
     * Pick one route for display and diagnostics. Compatibility itself remains an "any route"
     * decision, but the selected route must be deterministic and should prefer the route that
     * actually matches the pattern's RecipeMap and virtual circuit.
     */
    private Route selectRoute(PatternRouteKey routeKey, ICraftingPatternDetails details) {
        if (this.routes.isEmpty()) return null;
        if (details == null) return this.routes.get(0);

        ItemStack requiredCircuit = patternCircuit(details);
        if (routeKey != null && !routeKey.isEmpty()) {
            Route fallback = null;
            for (Route route : this.routes) {
                if (route.recipeMap == null || !routeKey.matches(route.recipeMap.unlocalizedName)) continue;
                if (fallback == null) fallback = route;
                if (requiredCircuit != null && PatternCircuitCompat.same(route.circuit, requiredCircuit)) return route;
                if (requiredCircuit == null && routeKey.hasCircuit() && routeKey.matchesCircuit(route.circuit))
                    return route;
                if (requiredCircuit == null && route.circuit == null) return route;
            }
            if (fallback != null) return fallback;
        }

        List<Route> candidates = new ArrayList<>();
        for (Route route : this.routes) {
            if (route.recipeMap != null && PatternRecipeMatcher.matches(route.recipeMap, details)) {
                candidates.add(route);
            }
        }

        // A RecipeMap hint is authoritative only when the target actually exposes that map.
        // If it is stale, keep the normal candidates so a manually entered pattern still gets a
        // useful machine/circuit tooltip (session discovery applies the same fallback policy).
        if (routeKey != null && !routeKey.isEmpty()) {
            List<Route> hinted = new ArrayList<>();
            for (Route route : candidates) {
                if (route.recipeMap != null && routeKey.matches(route.recipeMap.unlocalizedName)) hinted.add(route);
            }
            if (!hinted.isEmpty()) candidates = hinted;
        }

        if (requiredCircuit != null) {
            for (Route route : candidates) {
                if (PatternCircuitCompat.same(route.circuit, requiredCircuit)) return route;
            }
        }
        if (!candidates.isEmpty()) {
            // For a map match with an unknown circuit, expose a concrete route's circuit when
            // available instead of arbitrarily selecting an empty-circuit route first.
            for (Route route : candidates) {
                if (route.circuit != null) return route;
            }
            return candidates.get(0);
        }

        // No map could be matched (for example an interface whose adjacent machine is still
        // loading). Keep the old stable first-route behavior for display only.
        return this.routes.get(0);
    }

    public int getEmptySlots() {
        if (this.inventory == null) return 0;
        int empty = 0;
        for (int i = 0; i < this.slotCount; i++) {
            if (this.inventory.getStackInSlot(i) == null) empty++;
        }
        return empty;
    }

    public int firstEmptySlot(ItemStack pattern) {
        if (pattern == null || this.inventory == null) return -1;
        for (int i = 0; i < this.slotCount; i++) {
            ItemStack existing = this.inventory.getStackInSlot(i);
            if (existing != null && existing.isItemEqual(pattern)
                && ItemStack.areItemStackTagsEqual(existing, pattern)) {
                return -2;
            }
            if (existing == null && this.inventory.isItemValidForSlot(i, pattern)) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasPattern(ItemStack pattern) {
        return this.firstEmptySlot(pattern) == -2;
    }

    public boolean insert(ItemStack pattern) {
        if (!this.isStillOnGrid()) return false;
        int slot = this.firstEmptySlot(pattern);
        if (slot < 0) return false;
        return this.insertAt(slot, pattern);
    }

    public boolean insertAt(int slot, ItemStack pattern) {
        if (!this.isStillOnGrid() || pattern == null
            || this.inventory == null
            || slot < 0
            || slot >= this.slotCount
            || this.inventory.getStackInSlot(slot) != null
            || !this.inventory.isItemValidForSlot(slot, pattern)) {
            return false;
        }
        this.inventory.setInventorySlotContents(slot, pattern.copy());
        return true;
    }

    public boolean removeExact(ItemStack pattern) {
        if (!this.isStillOnGrid() || pattern == null || this.inventory == null) return false;
        for (int slot = 0; slot < this.slotCount; slot++) {
            ItemStack existing = this.inventory.getStackInSlot(slot);
            if (existing != null && existing.isItemEqual(pattern)
                && ItemStack.areItemStackTagsEqual(existing, pattern)) {
                this.inventory.setInventorySlotContents(slot, null);
                return true;
            }
        }
        return false;
    }

    public boolean removeExact(int slot, ItemStack pattern) {
        if (!this.isStillOnGrid() || pattern == null || this.inventory == null || slot < 0 || slot >= this.slotCount) {
            return false;
        }
        ItemStack existing = this.inventory.getStackInSlot(slot);
        if (existing == null || !existing.isItemEqual(pattern) || !ItemStack.areItemStackTagsEqual(existing, pattern)) {
            return false;
        }
        this.inventory.setInventorySlotContents(slot, null);
        return true;
    }

    private boolean isStillOnGrid() {
        if (!(this.host instanceof IGridHost)) return false;
        IGridNode node = ((IGridHost) this.host).getGridNode(ForgeDirection.UNKNOWN);
        return node != null && node.isActive() && node.getGrid() == this.grid;
    }

    private static List<Route> interfaceRoutes(IInterfaceViewable viewable, ItemStack routingIcon) {
        List<Route> routes = new ArrayList<>();
        if (viewable instanceof IMetaTileEntity) {
            IMetaTileEntity machine = (IMetaTileEntity) viewable;
            RecipeMap<?> map = recipeMap(machine);
            ItemStack circuit = circuitForMachine(machine);
            if (map != null || circuit != null) {
                ItemStack icon = machineIcon(machine);
                routes.add(new Route(map, circuit, ForgeDirection.UNKNOWN, icon, iconName(icon, machine)));
            }
        }
        if (viewable instanceof IInterfaceHost) {
            TileEntity tile = viewable.getTileEntity();
            if (tile != null && tile.getWorldObj() != null) {
                java.util.EnumSet<ForgeDirection> targets = ((IInterfaceHost) viewable).getTargets();
                if (targets == null) targets = java.util.EnumSet.noneOf(ForgeDirection.class);
                for (ForgeDirection direction : targets) {
                    if (direction == null || direction == ForgeDirection.UNKNOWN) continue;
                    TileEntity adjacent = tile.getWorldObj()
                        .getTileEntity(
                            tile.xCoord + direction.offsetX,
                            tile.yCoord + direction.offsetY,
                            tile.zCoord + direction.offsetZ);
                    if (!(adjacent instanceof IGregTechTileEntity)) continue;
                    IMetaTileEntity machine = ((IGregTechTileEntity) adjacent).getMetaTileEntity();
                    RecipeMap<?> map = recipeMap(machine);
                    ItemStack circuit = circuitForMachine(machine);
                    if (map != null || circuit != null) {
                        ItemStack icon = machineIcon(machine);
                        routes.add(new Route(map, circuit, direction, icon, iconName(icon, machine)));
                    }
                }
            }
        }
        boolean hasRecipeMap = false;
        for (Route route : routes) {
            if (route.recipeMap != null) {
                hasRecipeMap = true;
                break;
            }
        }
        if (!hasRecipeMap) {
            RecipeMap<?> iconMap = recipeMapFromIcon(routingIcon);
            if (iconMap != null) routes.add(new Route(iconMap, null, ForgeDirection.UNKNOWN, routingIcon, null));
        }
        return routes;
    }

    private static RecipeMap<?> recipeMapFromIcon(ItemStack routingIcon) {
        if (routingIcon == null || routingIcon.getItem() != Item.getItemFromBlock(GregTechAPI.sBlockMachines)) {
            return null;
        }
        int id = routingIcon.getItemDamage();
        if (id < 0 || id >= GregTechAPI.METATILEENTITIES.length) return null;
        return recipeMap(GregTechAPI.METATILEENTITIES[id]);
    }

    private static Kind interfaceKind(IInterfaceViewable viewable) {
        if (isProgrammableHatch(viewable)) return Kind.PROGRAMMABLE_HATCH;
        if (viewable instanceof MTEHatchCraftingInputME) {
            try {
                return ((MTEHatchCraftingInputME) viewable).supportsFluids() ? Kind.GT_CRAFTING_INPUT
                    : Kind.GT_CRAFTING_INPUT_BUS;
            } catch (RuntimeException ignored) {
                // A partially constructed hatch can briefly reject the capability query during chunk load.
                return Kind.GT_CRAFTING_INPUT_BUS;
            }
        }
        if (isAe2DualInterface(viewable)) {
            return Kind.AE2_DUAL_INTERFACE;
        }
        return Kind.AE2_INTERFACE;
    }

    private static boolean isProgrammableHatch(IInterfaceViewable viewable) {
        Class<?> type = viewable == null ? null : viewable.getClass();
        while (type != null) {
            String name = type.getName();
            if ("reobf.proghatches.gt.metatileentity.PatternDualInputHatch".equals(name)
                || "reobf.proghatches.gt.metatileentity.BufferedDualInputHatch".equals(name)
                || "reobf.proghatches.gt.metatileentity.DualInputHatch".equals(name)) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean isAe2DualInterface(IInterfaceViewable viewable) {
        if (viewable == null) return false;

        // Do not walk the superclass chain here. AE2FC's dual interface extends AE2's
        // ordinary interface implementation, and addon/mixin wrappers may expose the
        // ordinary interface through a shared base class. Treating every parent as a dual
        // interface was causing all targets to render as "ME Dual Interface".
        Class<?> type = viewable.getClass();
        String className = type.getName();
        if ("com.glodblock.github.common.tile.TileFluidInterface".equals(className)
            || "com.glodblock.github.common.parts.PartFluidInterface".equals(className)) {
            return true;
        }

        // A few AE2FC builds wrap the host object, while the representation still carries
        // the authoritative registered item/block identity. Use it only as a positive dual
        // signal; ordinary AE2 interfaces are never classified from a generic parent class.
        ItemStack representation = viewable.getDisplayRep();
        if (representation == null) representation = viewable.getSelfRep();
        if (representation == null || representation.getItem() == null) return false;
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(representation.getItem());
        if (id == null) return false;
        String path = id.name;
        return "fluid_interface".equals(path) || "part_fluid_interface".equals(path)
            || path.contains("fluid_p2p_interface");
    }

    private static RecipeMap<?> recipeMap(IMetaTileEntity machine) {
        if (machine == null) return null;
        try {
            if (machine instanceof MTEHatchInput) return ((MTEHatchInput) machine).mRecipeMap;
            if (machine instanceof MTEHatchInputBus) return ((MTEHatchInputBus) machine).mRecipeMap;
            if (machine instanceof RecipeMapWorkable) return ((RecipeMapWorkable) machine).getRecipeMap();
        } catch (RuntimeException ignored) {
            // A GT machine may be rebuilding its recipe map while the AE grid is refreshing.
        }
        return null;
    }

    private static ItemStack machineIcon(IMetaTileEntity machine) {
        if (machine == null) return null;
        try {
            if (machine instanceof ICraftingIconProvider) {
                ItemStack icon = ((ICraftingIconProvider) machine).getMachineCraftingIcon();
                return icon == null ? null : icon.copy();
            }
            return machine.getStackForm(1);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String iconName(ItemStack icon, IMetaTileEntity machine) {
        if (icon != null && icon.getDisplayName() != null
            && !icon.getDisplayName()
                .isEmpty()) {
            return icon.getDisplayName();
        }
        if (machine == null) return null;
        try {
            return machine.getMetaName();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static ItemStack patternCircuit(ICraftingPatternDetails details) {
        // An integrated circuit in a crafting recipe is a normal ingredient. Only processing
        // patterns use it as GT's virtual circuit selector.
        if (details == null || details.isCraftable() || details.getCondensedAEInputs() == null) return null;
        ItemStack fallback = null;
        for (IAEStack<?> input : details.getCondensedAEInputs()) {
            if (!(input instanceof IAEItemStack)) continue;
            ItemStack stack = ((IAEItemStack) input).getItemStack();
            if (stack == null) continue;
            if (stack.getItem() instanceof ItemIntegratedCircuit) return stack.copy();
            ItemStack unwrapped = PatternCircuitCompat.unwrap(stack);
            if (unwrapped != null && fallback == null) fallback = unwrapped;
        }
        return fallback;
    }

    private static ItemStack circuitForMachine(IMetaTileEntity machine) {
        if (!(machine instanceof IConfigurationCircuitSupport) || !(machine instanceof MetaTileEntity)) return null;
        try {
            int slot = ((IConfigurationCircuitSupport) machine).getCircuitSlot();
            if (slot < 0) return null;
            if (((MetaTileEntity) machine).getInventoryHandler() == null) return null;
            ItemStack circuit = ((MetaTileEntity) machine).getInventoryHandler()
                .getStackInSlot(slot);
            return circuit == null ? null : circuit.copy();
        } catch (RuntimeException ignored) {
            // Machines can be observed between construction and inventory initialization while
            // the network is rebuilding. Treat the circuit as unknown for this scan.
            return null;
        }
    }
}
