package cn.dancingsnow.neoecoae.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

class ECOFastPathCacheTest {

    @Test
    void negativeEntriesExpireFromCreationTick() {
        ECOFastPathCache cache = new ECOFastPathCache(16, 16);
        ECOFastPathPatternKey key = ECOFastPathPatternKey.of(new ItemStack(new Item()));

        cache.putNegative(key, "temporary", 10L);

        assertEquals("temporary", cache.getNegativeReason(key, 1209L));
        assertNull(cache.getNegativeReason(key, 1210L));
    }

    @Test
    void regressedWorldTimeInvalidatesNegativeEntries() {
        ECOFastPathCache cache = new ECOFastPathCache(16, 16);
        ECOFastPathPatternKey key = ECOFastPathPatternKey.of(new ItemStack(new Item()));

        cache.putNegative(key, "old world", 100L);

        assertNull(cache.getNegativeReason(key, 10L));
    }
}
