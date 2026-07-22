package cn.dancingsnow.neoecoae.crafting.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatternRouteKeyTest {

    @Test
    void trimsAndMatchesRecipeMapIdExactly() {
        PatternRouteKey key = new PatternRouteKey("  e fab  ");

        assertEquals("e fab", key.getRecipeMapId());
        assertTrue(key.matches("e fab"));
        assertFalse(key.matches("E FAB"));
        assertFalse(key.matches("e fab "));
    }

    @Test
    void nullAndBlankHintsAreEmpty() {
        PatternRouteKey nullKey = new PatternRouteKey(null);
        PatternRouteKey blankKey = new PatternRouteKey("  ");

        assertTrue(nullKey.isEmpty());
        assertTrue(blankKey.isEmpty());
        assertFalse(nullKey.matches("chemical_reactor"));
        assertFalse(blankKey.matches("chemical_reactor"));
    }

}
