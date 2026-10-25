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
        if (type == cn.dancingsnow.neoecoae.platform.ae2.storage.OptionalCellPower.class)
            return type.cast(cn.dancingsnow.neoecoae.platform.ae2.storage.OptionalCellPower.INSTANCE);
        if (appeng.api.networking.IGridCache.class.isAssignableFrom(type))
            return type.cast(grid.getCache((Class) type));
        throw new IllegalArgumentException("Unregistered native grid service: " + type.getName());
    }
}
