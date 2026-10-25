package cn.dancingsnow.neoecoae.api.me.planning;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/**
 * Options captured for one explicit ECO planning request.
 *
 * <p>
 * The legacy {@code planningLogEnabled} component remains for compatibility, but stage logging is now controlled
 * by the global {@code Debug > Calculating} configuration.
 * </p>
 */
@com.github.bsideup.jabel.Desugar
public record ECOPlannerOptions(boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
    Set<ResourceLocation> fuzzyPlanningItemIds, boolean planningLogEnabled) {

    public ECOPlannerOptions {
        fuzzyPlanningItemIds = fuzzyPlanningItemIds == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf()
            : fuzzyPlanningItemIds;
    }

    public ECOPlannerOptions(boolean cyclePlanningEnabled, boolean ignorePatternSubstitutions,
        Set<ResourceLocation> fuzzyPlanningItemIds) {
        this(cyclePlanningEnabled, ignorePatternSubstitutions, fuzzyPlanningItemIds, false);
    }

    public static ECOPlannerOptions from(@Nullable ECOCraftingNetworkSettings settings) {
        if (settings == null) {
            return new ECOPlannerOptions(false, false, cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf());
        }
        return new ECOPlannerOptions(
            settings.neoecoae$isCyclePlanningEnabled(),
            settings.neoecoae$isIgnoringPatternSubstitutions(),
            settings.neoecoae$getFuzzyPlanningItemIds());
    }
}
