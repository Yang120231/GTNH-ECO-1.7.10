package cn.dancingsnow.neoecoae.platform.structurelib;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;

/** Carries the structure-level channels that BlockRenderer's element scan cannot discover. */
final class NEPreviewConstructable implements ISurvivalConstructable {

    final int maximumLength;
    private final ISurvivalConstructable delegate;

    NEPreviewConstructable(ISurvivalConstructable delegate, MultiBlockDefinition definition) {
        this.delegate = delegate;
        maximumLength = definition == null ? 1 : Math.max(1, definition.getExpandMax());
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        delegate.construct(trigger, hintsOnly);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int budget, ISurvivalBuildEnvironment environment) {
        return delegate.survivalConstruct(trigger, budget, environment);
    }

    @Override
    public String[] getStructureDescription(ItemStack trigger) {
        return delegate.getStructureDescription(trigger);
    }
}
