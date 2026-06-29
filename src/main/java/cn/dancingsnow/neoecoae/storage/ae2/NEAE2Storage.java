package cn.dancingsnow.neoecoae.storage.ae2;

import appeng.api.AEApi;

public final class NEAE2Storage {

    private static boolean registered;

    private NEAE2Storage() {}

    public static void register() {
        if (registered) {
            return;
        }
        AEApi.instance()
            .registries()
            .cell()
            .addCellHandler(ECOCellHandler.INSTANCE);
        registered = true;
    }
}
