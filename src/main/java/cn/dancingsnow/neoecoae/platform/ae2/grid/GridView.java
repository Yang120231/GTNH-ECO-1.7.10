package cn.dancingsnow.neoecoae.platform.ae2.grid;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkEvent;
import appeng.api.networking.ticking.ITickManager;

public final class GridView {

    private final IGrid grid;
    private cn.dancingsnow.neoecoae.platform.ae2.storage.StorageServiceView storage;
    private static final java.util.Map<IGrid, java.lang.ref.WeakReference<GridView>> VIEWS = new java.util.WeakHashMap<>();

    private GridView(IGrid grid) {
        this.grid = grid;
    }

    public static synchronized GridView of(IGrid grid) {
        var ref = VIEWS.get(grid);
        GridView view = ref == null ? null : ref.get();
        if (view == null) {
            view = new GridView(grid);
            VIEWS.put(grid, new java.lang.ref.WeakReference<>(view));
        }
        return view;
    }

    public IGrid nativeGrid() {
        return grid;
    }

    public cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService getCraftingService() {
        return craftingService(defaultWorld(), false);
    }

    /** Planner switches only; never builds the recipe view. */
    public cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingNetworkSettings getCraftingNetworkSettings() {
        return tracking().settings();
    }

    public cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingServiceView captureCraftingService(
        net.minecraft.world.World world) {
        return craftingService(world, true);
    }

    public cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingServiceView captureCraftingService() {
        return craftingService(defaultWorld(), true);
    }

    private cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingServiceView craftingService(
        net.minecraft.world.World world, boolean capture) {
        return tracking().view(world, capture);
    }

    private cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternTrackingCache tracking() {
        cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternTrackingCache tracking = grid
            .getCache(cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternTrackingCache.class);
        if (tracking == null) throw new IllegalStateException("Native source-planning cache not registered");
        return tracking;
    }

    private net.minecraft.world.World defaultWorld() {
        var node = grid.getPivot();
        return node == null ? null : node.getWorld();
    }

    public java.util.Set<Class<?>> getMachineClasses() {
        var result = new java.util.LinkedHashSet<Class<?>>();
        for (var type : grid.getMachinesClasses()) result.add(sourceMachineClass(type));
        return result;
    }

    private static Class<?> sourceMachineClass(Class<?> type) {
        if (type == cn.dancingsnow.neoecoae.platform.registry.StorageMachines.Drive.class)
            return cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity.class;
        return type;
    }

    private static Class<?> nativeMachineClass(Class<?> type) {
        if (type == cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity.class)
            return cn.dancingsnow.neoecoae.platform.registry.StorageMachines.Drive.class;
        return type;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T> java.util.Set<T> getMachines(Class<T> type) {
        var result = new java.util.LinkedHashSet<T>();
        if (!appeng.api.networking.IGridHost.class.isAssignableFrom(type)) return result;
        for (var node : grid.getMachines((Class) nativeMachineClass(type))) {
            Object host = node.getMachine();
            if (type.isInstance(host)) result.add(type.cast(host));
        }
        return result;
    }

    public void postEvent(MENetworkEvent event) {
        grid.postEvent(event);
    }

    public ITickManager getTickManager() {
        return grid.getCache(ITickManager.class);
    }

    public IEnergyGrid getEnergyService() {
        return grid.getCache(IEnergyGrid.class);
    }

    public cn.dancingsnow.neoecoae.platform.ae2.storage.StorageServiceView getStorageService() {
        if (storage == null) storage = new cn.dancingsnow.neoecoae.platform.ae2.storage.StorageServiceView(
            grid.getCache(appeng.api.networking.storage.IStorageGrid.class));
        return storage;
    }

    public <T> T getService(Class<T> type) {
        if (appeng.api.networking.IGridCache.class.isAssignableFrom(type))
            return type.cast(grid.getCache((Class) type));
        throw new IllegalArgumentException("Unregistered native grid service: " + type.getName());
    }
}
