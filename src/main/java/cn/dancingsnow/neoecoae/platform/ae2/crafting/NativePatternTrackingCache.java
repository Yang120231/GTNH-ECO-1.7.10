package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingPostPatternChangeListener;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Native topology/provider invalidation for immutable source planner snapshots. No jobs or stock live here. */
public final class NativePatternTrackingCache implements IGridCache {

    private final IGrid grid;
    private final ICraftingPostPatternChangeListener patternsPublished = this::invalidate;
    private volatile long revision;
    private volatile long catalogRevision;
    private long recipeGeneration = PatternRuntime.reloadGeneration();
    private Set<AEKey> candidates = java.util.Collections.emptySet();
    private final Map<World, CraftingServiceView> views = new IdentityHashMap<>();
    private final Map<World, NativeLiveCraftingService> liveServices = new IdentityHashMap<>();
    private NativePatternPublication.Owner publication;

    public NativeLiveCraftingService live(World world) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        if (world == null) throw new IllegalStateException("No world for native execution service");
        return liveServices.computeIfAbsent(
            world,
            w -> new NativeLiveCraftingService(w, (CraftingGridCache) grid.getCache(ICraftingGrid.class), this));
    }

    boolean owns(World world, NativePatternDetails details) {
        var view = views.get(world);
        return view != null && view.owns(details) && view.isCatalogCurrent();
    }

    long liveRevision() {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        if (publication != null) publication.neoecoae$flushPatterns();
        checkRecipeGeneration();
        return catalogRevision;
    }

    /** Survives view invalidation: substitute acceptance depends only on the pattern, the key and the world. */
    private final Map<World, NativeSubstitutionIndex> substitutes = new java.util.WeakHashMap<>();
    private final NativeCraftingNetworkSettings settings;
    private long countedRevision = -1;
    private int substitutionPatterns;

    public NativePatternTrackingCache(IGrid grid) {
        this.grid = grid;
        settings = new NativeCraftingNetworkSettings(grid, this);
    }

    public NativeCraftingNetworkSettings settings() {
        return settings;
    }

    /** Counted from AE2's own pattern table, so a settings UI never builds the recipe view to show it. */
    synchronized int substitutionPatternCount() {
        if (countedRevision != revision) {
            Set<ICraftingPatternDetails> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            int count = 0;
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            for (var patterns : crafting.getCraftingMultiPatterns()
                .values())
                for (ICraftingPatternDetails pattern : patterns)
                    if (seen.add(pattern) && pattern.canSubstitute()) count++;
            substitutionPatterns = count;
            countedRevision = revision;
        }
        return substitutionPatterns;
    }

    public long revision() {
        return revision;
    }

    long catalogRevision() {
        if (publication != null && cn.dancingsnow.neoecoae.platform.network.ServerTasks.isServerThread())
            publication.neoecoae$flushPatterns();
        return catalogRevision;
    }

    private synchronized void invalidate() {
        revision++;
        catalogRevision++;
        views.clear();
    }

    @MENetworkEventSubscribe
    public void afterCacheConstruction(MENetworkPostCacheConstruction event) {
        ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        if (crafting instanceof NativePatternPublication.Owner owner) publication = owner;
        ((CraftingGridCache) crafting).addPostPatternChangeListeners(patternsPublished);
    }

    @Override
    public void addNode(IGridNode node, IGridHost machine) {
        if (affectsCraftingCatalog(machine)) invalidate();
    }

    @Override
    public void removeNode(IGridNode node, IGridHost machine) {
        if (affectsCraftingCatalog(machine)) invalidate();
    }

    private static boolean affectsCraftingCatalog(IGridHost machine) {
        return machine instanceof appeng.api.networking.crafting.ICraftingProvider
            || machine instanceof appeng.api.networking.crafting.ICraftingWatcherHost;
    }

    @Override
    public void onSplit(IGridStorage storage) {
        invalidate();
    }

    @Override
    public void onJoin(IGridStorage storage) {
        invalidate();
    }

    @Override
    public void populateGridStorage(IGridStorage storage) { /* Only transient native cache identities are kept. */ }

    @Override
    public void onUpdateTick() {}

    public synchronized CraftingServiceView capture(World world) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        if (world == null) throw new IllegalStateException("Cannot capture a recipe view without its world");
        liveRevision();
        long generation = recipeGeneration;
        var current = views.get(world);
        Set<AEKey> fresh = new HashSet<>(
            GridView.of(grid)
                .getStorageService()
                .getInventory()
                .getAvailableStacks()
                .keySet());
        ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
        fresh.addAll(catalogKeys(crafting));
        if (!fresh.equals(candidates)) {
            candidates = fresh;
            revision++;
            views.clear();
            current = null;
        }
        if (current == null) {
            NativeSubstitutionIndex index = substitutes.computeIfAbsent(world, NativeSubstitutionIndex::new);
            index.begin(fresh);
            current = new CraftingServiceView(grid, world, this, index, generation);
            index.retain(current.nativePatterns());
            views.put(world, current);
        }
        return current;
    }

    private void checkRecipeGeneration() {
        long generation = PatternRuntime.reloadGeneration();
        if (recipeGeneration != generation) {
            recipeGeneration = generation;
            substitutes.clear();
            invalidate();
        }
    }

    private static Set<AEKey> catalogKeys(ICraftingGrid crafting) {
        Set<AEKey> keys = new HashSet<>();
        for (var stack : crafting.getCraftingMultiPatterns()
            .keySet()) keys.add(AEKey.fromNative(stack));
        for (var stack : crafting.getEmitableItems()) keys.add(AEKey.fromNative(stack));
        keys.remove(null);
        return keys;
    }
}
