package cn.dancingsnow.neoecoae.client.render;

import java.util.HashMap;
import java.util.Map;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;

public final class ECOStorageCellRenderModels {

    private static final double DRIVE_CELL_OFFSET_X = 2.0D / 16.0D;
    private static final double DRIVE_CELL_OFFSET_Y = 2.0D / 16.0D;
    private static final double DRIVE_CELL_OFFSET_Z = 0.0D;
    private static final String MODEL_L4_ITEM = "cell/storage_cell_l4_item";
    private static final String MODEL_L6_ITEM = "cell/storage_cell_l6_item";
    private static final String MODEL_L9_ITEM = "cell/storage_cell_l9_item";
    private static final String[] PRELOAD_MODELS = { MODEL_L4_ITEM, MODEL_L6_ITEM, MODEL_L9_ITEM };
    private static final Map<String, BakedEcoModel> MODELS = new HashMap<>();

    private ECOStorageCellRenderModels() {}

    public static void preload() {
        for (String modelName : PRELOAD_MODELS) {
            load(modelName);
        }
    }

    public static BakedEcoModel getDriveCell(String tier) {
        return load(modelForTier(tier));
    }

    private static BakedEcoModel load(String modelName) {
        BakedEcoModel model = MODELS.get(modelName);
        if (model != null) {
            return model;
        }
        model = BakedEcoModel.offsetSubModel(
            ModernModelLoader.loadBlockModel(modelName),
            DRIVE_CELL_OFFSET_X,
            DRIVE_CELL_OFFSET_Y,
            DRIVE_CELL_OFFSET_Z);
        MODELS.put(modelName, model);
        NeoECOAE.LOG.debug("Loaded ECO storage cell model {} with {} quads", modelName, model.getMaxQuadCount());
        return model;
    }

    private static String modelForTier(String tier) {
        if ("16G".equals(tier) || "64M".equals(tier)) {
            return MODEL_L6_ITEM;
        }
        if ("64G".equals(tier) || "256M".equals(tier)) {
            return MODEL_L9_ITEM;
        }
        return MODEL_L4_ITEM;
    }
}
