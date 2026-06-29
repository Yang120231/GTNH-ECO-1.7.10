package cn.dancingsnow.neoecoae.client.render;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;

public final class ComputationCellItemModels {

    private static final Map<String, BakedEcoModel> MODELS = new HashMap<String, BakedEcoModel>();

    private ComputationCellItemModels() {}

    public static void preload() {
        load("eco_computation_cell_l4");
        load("eco_computation_cell_l6");
        load("eco_computation_cell_l9");
    }

    public static BakedEcoModel get(String tier) {
        return load(modelForTier(tier));
    }

    private static BakedEcoModel load(String modelName) {
        BakedEcoModel model = MODELS.get(modelName);
        if (model != null) {
            return model;
        }
        model = new BakedEcoModel(
            ModernModelLoader.loadModel(new ResourceLocation(NeoECOAE.MODID, "models/item/" + modelName + ".json"), 0));
        MODELS.put(modelName, model);
        NeoECOAE.LOG
            .debug("Loaded ECO computation cell item model {} with {} quads", modelName, model.getMaxQuadCount());
        return model;
    }

    private static String modelForTier(String tier) {
        if ("CE9".equals(tier)) {
            return "eco_computation_cell_l9";
        }
        if ("CE6".equals(tier)) {
            return "eco_computation_cell_l6";
        }
        return "eco_computation_cell_l4";
    }
}
