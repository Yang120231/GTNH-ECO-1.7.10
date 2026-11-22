package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.TickClock;

final class ECOExactRefillSchedule implements IMEMonitorHandlerReceiver<IAEStack> {

    private static final int ATTEMPTS_PER_TICK = 32;
    private static final int NO_PROGRESS_RETRY_TICKS = 20;

    private final Map<AEKey, Long> deadlines = new HashMap<>();
    private final TreeMap<Long, LinkedHashSet<AEKey>> due = new TreeMap<>();
    private final Set<IBaseMonitor<?>> monitors = new HashSet<>();
    private GridView grid;
    private long budgetTick = Long.MIN_VALUE;
    private int attempts;

    void bind(GridView nextGrid, Set<AEKey> missing, long now) {
        if (grid == nextGrid) return;
        if (grid != null) clear();
        grid = nextGrid;
        IStorageGrid storage = grid.getService(IStorageGrid.class);
        for (var key : missing) {
            schedule(key, now);
            var type = key.getType()
                .nativeType();
            var monitor = type == null ? null : storage.getMEMonitor(type);
            if (monitor != null && monitors.add(monitor)) monitor.addListener(this, monitor);
        }
    }

    boolean hasDue(long now) {
        return !due.isEmpty() && due.firstKey() <= now;
    }

    AEKey poll(long now) {
        if (budgetTick != now) {
            budgetTick = now;
            attempts = 0;
        }
        if (attempts >= ATTEMPTS_PER_TICK || !hasDue(now)) return null;
        var first = due.firstEntry();
        var iterator = first.getValue()
            .iterator();
        var key = iterator.next();
        iterator.remove();
        if (first.getValue()
            .isEmpty()) due.pollFirstEntry();
        deadlines.remove(key);
        attempts++;
        return key;
    }

    void retry(AEKey key, long now, boolean progress) {
        if (grid != null) schedule(key, now + (progress ? 1 : NO_PROGRESS_RETRY_TICKS));
    }

    void clearIfGridChanged(GridView currentGrid) {
        if (grid != null && grid != currentGrid) clear();
    }

    void clear() {
        for (var monitor : monitors) monitor.removeListener(this);
        monitors.clear();
        due.clear();
        deadlines.clear();
        grid = null;
    }

    @Override
    public boolean isValid(Object token) {
        return monitors.contains(token);
    }

    @Override
    public void postChange(IBaseMonitor<IAEStack> monitor, Iterable<IAEStack> changes, BaseActionSource source) {
        long now = TickClock.currentTick();
        for (var change : changes) {
            if (change == null) continue;
            var key = AEKey.fromNative(change);
            Long previous = deadlines.get(key);
            if (previous == null || previous <= now) continue;
            var bucket = due.get(previous);
            bucket.remove(key);
            if (bucket.isEmpty()) due.remove(previous);
            schedule(key, now);
        }
    }

    @Override
    public void onListUpdate() {}

    private void schedule(AEKey key, long tick) {
        deadlines.put(key, tick);
        due.computeIfAbsent(tick, ignored -> new LinkedHashSet<>())
            .add(key);
    }
}
