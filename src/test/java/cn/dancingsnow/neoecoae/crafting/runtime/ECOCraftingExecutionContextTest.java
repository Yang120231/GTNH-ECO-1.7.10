package cn.dancingsnow.neoecoae.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ECOCraftingExecutionContextTest {

    @Test
    void restoresNestedJobContext() {
        assertNull(ECOCraftingExecutionContext.currentJobId());
        try (ECOCraftingExecutionContext.Scope outer = ECOCraftingExecutionContext.enter("outer")) {
            assertEquals("outer", ECOCraftingExecutionContext.currentJobId());
            try (ECOCraftingExecutionContext.Scope inner = ECOCraftingExecutionContext.enter("inner")) {
                assertEquals("inner", ECOCraftingExecutionContext.currentJobId());
            }
            assertEquals("outer", ECOCraftingExecutionContext.currentJobId());
        }
        assertNull(ECOCraftingExecutionContext.currentJobId());
    }
}
