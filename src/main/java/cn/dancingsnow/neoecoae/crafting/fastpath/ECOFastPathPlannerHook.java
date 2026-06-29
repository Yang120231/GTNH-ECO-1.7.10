package cn.dancingsnow.neoecoae.crafting.fastpath;

import net.minecraft.inventory.InventoryCrafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public final class ECOFastPathPlannerHook {

    private static final ECOFastPathPatternInspector INSPECTOR = new ECOFastPathPatternInspector();
    private static final ECOFastPathCache CACHE = new ECOFastPathCache();

    private ECOFastPathPlannerHook() {}

    public static ECOFastPathPlan tryPlan(TileECOController controller, ICraftingPatternDetails patternDetails,
        InventoryCrafting table) {
        if (!ECOFastPathConfig.isPlannerHookEnabled()) {
            return ECOFastPathPlan.rejected(ECOFastPathDecision.DISABLED, "fast path disabled");
        }
        if (!isEcoCraftingHost(controller)) {
            return ECOFastPathPlan.rejected(ECOFastPathDecision.NOT_ECO_CRAFTING_HOST, "not an ECO crafting host");
        }
        ECOFastPathPatternKey key = ECOFastPathPatternKey.of(patternDetails);
        String negativeReason = CACHE.getNegativeReason(key);
        if (negativeReason != null) {
            return ECOFastPathPlan.rejected(ECOFastPathDecision.CACHE_NEGATIVE, negativeReason);
        }
        ECOFastPathPatternProfile cached = CACHE.getProfile(key);
        if (cached != null) {
            return ECOFastPathPlan.accepted(cached);
        }
        try {
            ECOFastPathPatternProfile profile = INSPECTOR.inspect(patternDetails);
            CACHE.putProfile(profile);
            return ECOFastPathPlan.accepted(profile);
        } catch (ECOFastPathPatternException e) {
            CACHE.putNegative(key, e.getMessage());
            return ECOFastPathPlan.rejected(ECOFastPathDecision.UNSAFE_PATTERN, e.getMessage());
        } catch (RuntimeException e) {
            String reason = e.getClass()
                .getSimpleName();
            CACHE.putNegative(key, reason);
            return ECOFastPathPlan.rejected(ECOFastPathDecision.ERROR, reason);
        }
    }

    public static void clearCaches() {
        CACHE.clear();
    }

    public static int profileCacheSize() {
        return CACHE.profileSize();
    }

    public static int negativeCacheSize() {
        return CACHE.negativeSize();
    }

    private static boolean isEcoCraftingHost(TileECOController controller) {
        return controller != null && controller.getSubsystem() == ECOControllerSubsystem.CRAFTING
            && controller.isFormed();
    }
}
