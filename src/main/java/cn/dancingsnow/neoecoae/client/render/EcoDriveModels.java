package cn.dancingsnow.neoecoae.client.render;

import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;

public final class EcoDriveModels {

    private static BakedEcoModel empty;
    private static BakedEcoModel full;

    private EcoDriveModels() {}

    public static void load() {
        empty = new BakedEcoModel(ModernModelLoader.loadBlockModel("eco_drive_empty"));
        full = new BakedEcoModel(ModernModelLoader.loadBlockModel("eco_drive_full"));
    }

    public static BakedEcoModel get(EcoDriveVisualState state) {
        ensureLoaded();
        return state == EcoDriveVisualState.FULL ? full : empty;
    }

    private static void ensureLoaded() {
        if (empty == null || full == null) {
            load();
        }
    }
}
