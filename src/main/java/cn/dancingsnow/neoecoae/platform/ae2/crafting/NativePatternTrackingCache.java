package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.lang.reflect.Field;
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
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Native topology/provider invalidation for immutable source planner snapshots. No jobs or stock live here. */
public final class NativePatternTrackingCache implements IGridCache {

    private static final Field REFRESH = refreshField();
    private final IGrid grid;
    private volatile long revision;
    private volatile boolean stable;
    private Set<AEKey> candidates = java.util.Collections.emptySet();
    private final Map<World, CraftingServiceView> views = new IdentityHashMap<>();

    public NativePatternTrackingCache(IGrid grid) {
        this.grid = grid;
    }

    private static Field refreshField() {
        try {
            Field field = CraftingGridCache.class.getDeclaredField("updateList");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned native provider refresh field changed", failure);
        }
    }

    public long revision() {
        return revision;
    }

    public boolean stable() {
        return stable;
    }

    private synchronized void invalidate() {
        revision++;
        stable = false;
        views.clear();
    }

    @MENetworkEventSubscribe
    public void patternsChanged(MENetworkCraftingPatternChange event) {
        invalidate();
    }

    @Override
    public void addNode(IGridNode node, IGridHost machine) {
        invalidate();
    }

    @Override
    public void removeNode(IGridNode node, IGridHost machine) {
        invalidate();
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
    public void onUpdateTick() {
        try {
            stable = !REFRESH.getBoolean(grid.getCache(ICraftingGrid.class));
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot inspect native provider refresh", failure);
        }
    }

    public synchronized CraftingServiceView view(World world, boolean captureInventory) {
        cn.dancingsnow.neoecoae.platform.network.ServerTasks.assertServerThread();
        if (world == null) throw new IllegalStateException("Cannot capture a recipe view without its world");
        var current = views.get(world);
        if (!captureInventory && current != null) return current;
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
            current = new CraftingServiceView(grid, world, fresh, this);
            views.put(world, current);
        }
        return current;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Set<AEKey> catalogKeys(ICraftingGrid crafting) {
        if (!(crafting instanceof CraftingGridCache))
            throw new IllegalStateException("Unmapped native crafting catalog");
        Set<AEKey> keys = new HashSet<>();
        // The public native listing includes emitters that have no stored stock and no crafting pattern.
        for (var channel : appeng.api.storage.StorageChannel.values()) {
            appeng.api.storage.data.IItemList list = channel.createList();
            ((CraftingGridCache) crafting).getAvailableItems(list, 0, java.util.Optional.empty());
            for (Object entry : list) {
                AEKey key = AEKey.fromNative((appeng.api.storage.data.IAEStack<?>) entry);
                if (key != null) keys.add(key);
            }
        }
        return keys;
    }
}
