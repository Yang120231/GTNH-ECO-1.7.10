package cn.dancingsnow.neoecoae.client.render;

import java.util.HashMap;
import java.util.Map;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;

public final class DriveModels {

    private static final Map<String, BakedEcoModel> EMPTY_MODELS = new HashMap<String, BakedEcoModel>();
    private static final Map<String, BakedEcoModel> FULL_MODELS = new HashMap<String, BakedEcoModel>();

    private DriveModels() {}

    public static void load(BlockModelDrive block) {
        BakedEcoModel emptyModel = new BakedEcoModel(ModernModelLoader.loadBlockModel(block.getEmptyModelName()));
        BakedEcoModel fullModel = new BakedEcoModel(ModernModelLoader.loadBlockModel(block.getFullModelName()));
        EMPTY_MODELS.put(block.getEmptyModelName(), emptyModel);
        FULL_MODELS.put(block.getFullModelName(), fullModel);
        NeoECOAE.LOG.debug(
            "Loaded drive models {}/{} with {}/{} quads",
            block.getEmptyModelName(),
            block.getFullModelName(),
            emptyModel.getMaxQuadCount(),
            fullModel.getMaxQuadCount());
    }

    public static BakedEcoModel get(BlockModelDrive block, DriveVisualState state) {
        ensureLoaded(block);
        if (state == DriveVisualState.FULL) {
            return FULL_MODELS.get(block.getFullModelName());
        }
        return EMPTY_MODELS.get(block.getEmptyModelName());
    }

    private static void ensureLoaded(BlockModelDrive block) {
        if (!EMPTY_MODELS.containsKey(block.getEmptyModelName())
            || !FULL_MODELS.containsKey(block.getFullModelName())) {
            NeoECOAE.LOG.warn(
                "Lazy loading drive models {}/{}; preload them in ClientProxy",
                block.getEmptyModelName(),
                block.getFullModelName());
            load(block);
        }
    }
}
