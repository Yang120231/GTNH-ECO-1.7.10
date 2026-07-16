package cn.dancingsnow.neoecoae.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ECOCraftingOutputFlushContextTest {

    @Test
    void marksOnlyTheCurrentOutputFlushScope() {
        assertFalse(ECOCraftingOutputFlushContext.isActive());
        try (ECOCraftingOutputFlushContext.Scope ignored = ECOCraftingOutputFlushContext.enter()) {
            assertTrue(ECOCraftingOutputFlushContext.isActive());
        }
        assertFalse(ECOCraftingOutputFlushContext.isActive());
    }

    @Test
    void nestedFlushKeepsContextActiveUntilOuterScopeEnds() {
        try (ECOCraftingOutputFlushContext.Scope outer = ECOCraftingOutputFlushContext.enter()) {
            try (ECOCraftingOutputFlushContext.Scope inner = ECOCraftingOutputFlushContext.enter()) {
                assertTrue(ECOCraftingOutputFlushContext.isActive());
            }
            assertTrue(ECOCraftingOutputFlushContext.isActive());
        }
        assertFalse(ECOCraftingOutputFlushContext.isActive());
    }
}
