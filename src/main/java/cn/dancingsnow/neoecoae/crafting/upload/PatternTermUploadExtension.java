package cn.dancingsnow.neoecoae.crafting.upload;

import net.minecraft.item.ItemStack;

public interface PatternTermUploadExtension {

    void neoecoae$encodeAndPrepareUpload();

    void neoecoae$openUpload(boolean autoUpload);

    void neoecoae$setRouteContext(String recipeMapId, ItemStack circuit);
}
