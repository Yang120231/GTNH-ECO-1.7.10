package cn.dancingsnow.neoecoae.all;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NEOreDictionaryTest {

    @Test
    void neoTungstenUsesGregTechDustButKeepsOtherMaterialKeysPrivate() {
        assertPrivateKey(NEOreDictionary.NEO_TUNGSTEN_ORE, "oreTungsten");
        assertPrivateKey(NEOreDictionary.NEO_TUNGSTEN_RAW, "rawTungsten");
        assertPrivateKey(NEOreDictionary.NEO_TUNGSTEN_INGOT, "ingotTungsten");
        assertEquals("dustTungsten", NEOreDictionary.NEO_TUNGSTEN_DUST);
        assertPrivateKey(NEOreDictionary.NEO_TUNGSTEN_RAW_BLOCK, "blockRawTungsten");
        assertPrivateKey(NEOreDictionary.NEO_TUNGSTEN_BLOCK, "blockTungsten");
    }

    private static void assertPrivateKey(String actual, String forbidden) {
        assertTrue(actual.startsWith("neoecoae"), "private material keys must use the NeoECO namespace");
        assertFalse(actual.equals(forbidden), "NeoECO tungsten must not use GT-compatible key " + forbidden);
    }
}
