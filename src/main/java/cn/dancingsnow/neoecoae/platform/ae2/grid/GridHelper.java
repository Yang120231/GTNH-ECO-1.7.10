package cn.dancingsnow.neoecoae.platform.ae2.grid;

public final class GridHelper {

    private GridHelper() {}

    public static <T extends NativeNetworkBlockEntity> ManagedNode createManagedNode(T owner,
        NodeListener<? super T> listener) {
        return new ManagedNode(owner, listener);
    }
}
