package cn.dancingsnow.neoecoae.platform.ui;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.cleanroommc.modularui.value.sync.BigIntSyncValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/**
 * Deterministic typed binding boundary for imported pages. Quantities are server-authored, actions return to its tick.
 */
public final class Mui2Bindings {

    private final SortedMap<String, SyncHandler> values = new TreeMap<>();
    private final SortedMap<String, Action> actions = new TreeMap<>();
    private boolean installed;

    public LongSyncValue longValue(String key, LongSupplier getter) {
        return add(key, new LongSyncValue(getter));
    }

    public IntSyncValue intValue(String key, IntSupplier getter) {
        return add(key, new IntSyncValue(getter));
    }

    public BooleanSyncValue booleanValue(String key, BooleanSupplier getter) {
        return add(key, new BooleanSyncValue(getter));
    }

    public BigIntSyncValue exactValue(String key, Supplier<BigInteger> getter) {
        return add(key, new BigIntSyncValue(getter, null));
    }

    private <T extends SyncHandler> T add(String key, T value) {
        checkNew(key);
        values.put(key, value);
        return value;
    }

    public void serverAction(String key, BooleanSupplier sessionValid, Runnable action) {
        checkNew(key);
        actions.put(key, new Action(Objects.requireNonNull(sessionValid), Objects.requireNonNull(action)));
    }

    private void checkNew(String key) {
        if (installed) throw new IllegalStateException("MUI binding schema is frozen");
        if (key == null || !key.matches("[a-z0-9_.:/-]+"))
            throw new IllegalArgumentException("Invalid MUI binding key");
        if (values.containsKey(key) || actions.containsKey(key))
            throw new IllegalArgumentException("Duplicate MUI binding key: " + key);
    }

    public void install(PanelSyncManager sync) {
        if (installed) throw new IllegalStateException("MUI binding schema installed twice");
        installed = true;
        ServerTasks.Session serverSession = ServerTasks.captureSession();
        values.forEach(sync::syncValue);
        actions.forEach(
            (key, action) -> sync.registerSyncedAction(
                key,
                false,
                true,
                buffer -> serverSession.execute(() -> { if (action.sessionValid.getAsBoolean()) action.task.run(); })));
    }

    public List<String> schema() {
        List<String> schema = new ArrayList<>();
        values.forEach(
            (key, value) -> schema.add(
                "value:" + key
                    + ":"
                    + value.getClass()
                        .getName()));
        actions.keySet()
            .forEach(key -> schema.add("server_action:" + key));
        return Collections.unmodifiableList(schema);
    }

    private static final class Action {

        final BooleanSupplier sessionValid;
        final Runnable task;

        Action(BooleanSupplier sessionValid, Runnable task) {
            this.sessionValid = sessionValid;
            this.task = task;
        }
    }
}
