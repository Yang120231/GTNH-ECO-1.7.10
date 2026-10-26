package cn.dancingsnow.neoecoae.crafting.execution;

import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;

/** Live world and native link owner required to restore a complete persisted ECO job. */
public interface CraftingJobPersistenceContext {

    World getLevel();

    appeng.api.networking.crafting.ICraftingCPU nativeCPU();

    default IPatternDetails decodePattern(AEItemKey definition) {
        return PatternDetailsHelper.decodePattern(definition, getLevel());
    }
}
