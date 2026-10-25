package cn.dancingsnow.neoecoae.platform.ae2.grid;

import appeng.api.networking.security.BaseActionSource;

/** Keeps the native security identity at the API boundary. */
public final class IActionSource {

    private static final IActionSource EMPTY = new IActionSource(new BaseActionSource());
    private final BaseActionSource source;

    private IActionSource(BaseActionSource source) {
        this.source = java.util.Objects.requireNonNull(source);
    }

    public static IActionSource empty() {
        return EMPTY;
    }

    public static IActionSource ofMachine(appeng.api.networking.security.IActionHost host) {
        return of(new appeng.api.networking.security.MachineSource(host));
    }

    public static IActionSource of(BaseActionSource source) {
        return source == null ? EMPTY : new IActionSource(source);
    }

    public BaseActionSource nativeSource() {
        return source;
    }
}
