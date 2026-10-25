package cn.dancingsnow.neoecoae.platform;

public final class InventoryTestBootstrap {

    private InventoryTestBootstrap() {}

    /** Algorithm tests use an isolated identity registry; client and server acceptance exercise FML registries. */
    public static void initialize() {
        TestItemRegistry.initialize();
    }
}
