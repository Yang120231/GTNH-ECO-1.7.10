package cn.dancingsnow.neoecoae.client.render;

import java.util.HashMap;
import java.util.Map;

import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;

public final class ModernBlockModels {

    private static final Map<String, BakedEcoModel> MODELS = new HashMap<String, BakedEcoModel>();

    private ModernBlockModels() {}

    public static void load(String modelName) {
        MODELS.put(modelName, new BakedEcoModel(ModernModelLoader.loadBlockModel(modelName)));
    }

    public static BakedEcoModel get(String modelName) {
        BakedEcoModel model = MODELS.get(modelName);
        if (model == null) {
            load(modelName);
            model = MODELS.get(modelName);
        }
        return model;
    }
}
