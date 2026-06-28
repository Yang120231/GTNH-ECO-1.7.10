package cn.dancingsnow.neoecoae.client.render;

import java.util.HashMap;
import java.util.Map;

import cn.dancingsnow.neoecoae.block.BlockModelDrive;
import cn.dancingsnow.neoecoae.client.render.model.BakedEcoModel;
import cn.dancingsnow.neoecoae.client.render.model.ModernModelLoader;

public final class DriveModels {

    private static final Map<String, BakedEcoModel> EMPTY_MODELS = new HashMap<String, BakedEcoModel>();
    private static final Map<String, BakedEcoModel> FULL_MODELS = new HashMap<String, BakedEcoModel>();

    private DriveModels() {}

    public static void load(BlockModelDrive block) {
        EMPTY_MODELS.put(
            block.getEmptyModelName(),
            new BakedEcoModel(ModernModelLoader.loadBlockModel(block.getEmptyModelName())));
        FULL_MODELS.put(
            block.getFullModelName(),
            new BakedEcoModel(ModernModelLoader.loadBlockModel(block.getFullModelName())));
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
            load(block);
        }
    }
}
