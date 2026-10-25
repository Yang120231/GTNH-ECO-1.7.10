package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ECOExecutionContractGenerationTest {

    @Test
    void preparedExecutionLosesEligibilityAfterContractChange() {
        ECOFastPathKey key = ECOFastPathKey.of(new Object(), new KeyCounter[0], null, PatternRuntime.reloadGeneration())
            .orElseThrow(java.util.NoSuchElementException::new);
        ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.ofNormalizedComponents(
            key,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        assertTrue(execution.fastPathEligible());
        PatternRuntime.onRecipeReloadOrServerReload();
        assertFalse(execution.fastPathEligible());
    }

    @Test
    void oldGenerationCredentialCannotBeReissuedAfterCacheWasCleared() {
        long generation = PatternRuntime.reloadGeneration();
        ECOFastPathKey key = ECOFastPathKey.of(new Object(), new KeyCounter[0], null, generation)
            .orElseThrow(java.util.NoSuchElementException::new);
        ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.ofNormalizedComponents(
            key,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(16);
        ECOFastPathResult result = ECOFastPathResult.positive(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            0L);
        ECOVerifiedFastPathRecipe before = ECOVerifiedFastPathRecipe.trusted(cache, execution, key, result, generation);
        assertTrue(before.isIssuedBy(cache));
        PatternRuntime.onRecipeReloadOrServerReload();
        assertFalse(before.isIssuedBy(cache));
        // Simulate a lookup that finished late, after clear, using an obsolete caller-supplied generation.
        ECOVerifiedFastPathRecipe late = ECOVerifiedFastPathRecipe.trusted(cache, execution, key, result, generation);
        assertFalse(late.isIssuedBy(cache));
    }

    @Test
    void cacheWritesAndRegistryInvalidationMayOverlap() throws Exception {
        ECOCraftingFastPathCache cache = new ECOCraftingFastPathCache(16);
        ECOFastPathKey key = ECOFastPathKey.of(new Object(), new KeyCounter[0], null, PatternRuntime.reloadGeneration())
            .orElseThrow(java.util.NoSuchElementException::new);
        try (var executor = cn.dancingsnow.neoecoae.platform.jdk.CloseableExecutor.fixed(2)) {
            var writes = executor.submit(() -> {
                for (int i = 0; i < 500; i++) {
                    cache.putNegative(key, i);
                    cache.get(key, i);
                }
            });
            var invalidations = executor
                .submit(() -> { for (int i = 0; i < 500; i++) PatternRuntime.onRecipeReloadOrServerReload(); });
            writes.get(10, TimeUnit.SECONDS);
            invalidations.get(10, TimeUnit.SECONDS);
        }
        cache.putNegative(key, 1000L, "STILL_USABLE");
        assertEquals(
            "STILL_USABLE",
            cache.get(key, 1000L)
                .rejectReason());
    }
}
