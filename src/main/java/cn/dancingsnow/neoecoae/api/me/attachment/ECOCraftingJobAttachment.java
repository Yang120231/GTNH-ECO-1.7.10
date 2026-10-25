package cn.dancingsnow.neoecoae.api.me.attachment;

import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/**
 * Per-job extension state owned and persisted by ECO.
 *
 * <p>
 * An attachment instance belongs to one job. Register a factory with
 * {@link ECOCraftingJobAttachmentRegistry} so ECO can create one instance for each new or restored job.
 * </p>
 */
public interface ECOCraftingJobAttachment {

    ResourceLocation id();

    CompoundTag save(HolderLookup.Provider registries);

    void load(CompoundTag tag, HolderLookup.Provider registries);

    void clear(ECOCraftingJobResult result);
}
