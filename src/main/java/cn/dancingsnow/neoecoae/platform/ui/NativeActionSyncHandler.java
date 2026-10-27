package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.value.sync.SyncHandler;

/** Native action protocols validate every request in their server-side receiver. */
public abstract class NativeActionSyncHandler extends SyncHandler {

    // MUI2 2.3.49 has no C2S opt-in API. Newer runtimes call this method on both sides;
    // omit @Override so the same JAR remains compatible with the pinned older API.
    public final boolean isAllowC2S() {
        return true;
    }
}
