package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;

/** Core ECO capability and native AE2 blocking-state access. */
public final class NativeProviderAdapters {

    private NativeProviderAdapters() {}

    public interface BlockingState {

        boolean isBlocking();
    }

    public static ECOFastPathDispatchProvider resolve(ICraftingProvider provider) {
        if (provider instanceof ECOFastPathDispatchProvider) return (ECOFastPathDispatchProvider) provider;
        return null;
    }

    public static boolean isEnabled(ICraftingProvider provider) {
        return provider instanceof BlockingState && ((BlockingState) provider).isBlocking();
    }
}
