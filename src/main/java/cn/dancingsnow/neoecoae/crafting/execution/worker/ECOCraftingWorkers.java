package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;

/** Loaded worker identity index. Missing/unloaded workers remain governed by durable job decisions. */
public final class ECOCraftingWorkers {

    private static final Set<ECOCraftingWorkerHandle> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private ECOCraftingWorkers() {}

    public static synchronized void loaded(ECOCraftingWorkerHandle worker) {
        LOADED.add(worker);
    }

    public static synchronized void unloaded(ECOCraftingWorkerHandle worker) {
        LOADED.remove(worker);
    }

    public static synchronized List<ECOCraftingWorkerHandle> loaded() {
        return new ArrayList<>(LOADED);
    }

    public static List<ECOCraftingWorkerHandle> onGrid(GridView grid) {
        List<ECOCraftingWorkerHandle> result = loaded();
        result.removeIf(worker -> worker.getWorkerGrid() != grid);
        return result;
    }
}
