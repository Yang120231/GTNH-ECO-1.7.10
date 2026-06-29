package cn.dancingsnow.neoecoae.crafting.ae2;

import java.lang.reflect.Field;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.NeoECOAE;

final class ECOCraftingProviderBridge {

    private static Field craftingProvidersField;

    private ECOCraftingProviderBridge() {}

    static boolean register(ICraftingGrid grid, ICraftingProvider provider) {
        Set<ICraftingProvider> providers = craftingProviders(grid);
        if (providers == null) {
            return false;
        }
        providers.add(provider);
        return true;
    }

    static boolean unregister(ICraftingGrid grid, ICraftingProvider provider) {
        Set<ICraftingProvider> providers = craftingProviders(grid);
        return providers != null && providers.remove(provider);
    }

    @SuppressWarnings("unchecked")
    private static Set<ICraftingProvider> craftingProviders(ICraftingGrid grid) {
        if (!(grid instanceof CraftingGridCache)) {
            return null;
        }
        try {
            if (craftingProvidersField == null) {
                craftingProvidersField = CraftingGridCache.class.getDeclaredField("craftingProviders");
                craftingProvidersField.setAccessible(true);
            }
            return (Set<ICraftingProvider>) craftingProvidersField.get(grid);
        } catch (ReflectiveOperationException e) {
            NeoECOAE.LOG.error("Unable to access AE2 crafting provider registry", e);
            return null;
        }
    }
}
