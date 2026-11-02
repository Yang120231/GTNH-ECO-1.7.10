package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class ECOProcessingExecutionPatternTest {

    @Test
    void providerCatalogRecognizesAnEquivalentSeparatelyDecodedPattern() {
        var original = new ContentPattern("recipe");
        var decoded = new ContentPattern("recipe");
        assertNotSame(original, decoded);
        assertEquals(original, decoded);
        var scaled = new ECOProcessingExecutionPattern(original, 64);
        assertTrue(new ArrayList<>(Collections.singletonList(decoded)).contains(scaled));
        assertEquals(scaled, new ECOProcessingExecutionPattern(decoded, 64));
        assertEquals(scaled.hashCode(), decoded.hashCode());
        assertNotEquals(scaled, new ECOProcessingExecutionPattern(decoded, 32));
        assertNotEquals(scaled, new ContentPattern("other"));
        assertNotEquals(scaled, null);
    }

    /** A provider's value-based recipe contract, independently decoded for the task and catalog. */
    private static final class ContentPattern implements IPatternDetails {

        private final String recipe;

        private ContentPattern(String recipe) {
            this.recipe = recipe;
        }

        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return Collections.emptyList();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ContentPattern && recipe.equals(((ContentPattern) other).recipe);
        }

        @Override
        public int hashCode() {
            return recipe.hashCode();
        }
    }
}
