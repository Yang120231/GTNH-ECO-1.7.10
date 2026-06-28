package cn.dancingsnow.neoecoae.client.render;

import java.util.HashMap;
import java.util.Map;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;

public final class ModernBlockModels {

    private static final Map<String, BakedEcoModel> MODELS = new HashMap<>();

    private ModernBlockModels() {}

    public static void load(String modelName) {
        if (MODELS.containsKey(modelName)) {
            return;
        }
        BakedEcoModel model = new BakedEcoModel(ModernModelLoader.loadBlockModel(modelName));
        MODELS.put(modelName, model);
        NeoECOAE.LOG.debug("Loaded modern block model {} with {} quads", modelName, model.getMaxQuadCount());
    }

    public static BakedEcoModel get(String modelName) {
        BakedEcoModel model = MODELS.get(modelName);
        if (model == null) {
            NeoECOAE.LOG.warn("Lazy loading modern block model {}; preload it in ClientProxy", modelName);
            load(modelName);
            model = MODELS.get(modelName);
        }
        return model;
    }
}
