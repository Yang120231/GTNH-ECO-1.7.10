package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningResult.Status;

class ECOPlanningEngineTest {

    @Test
    void separateSccsImportThroughAcyclicBoundary() {
        ECOPlanningResult<String, String> result = plan(
            "x",
            100,
            amounts("a", 1, "x", 1),
            recipe("ab", amounts("a", 1), amounts("b", 1)),
            recipe("ba", amounts("b", 1), amounts("a", 2)),
            recipe("fuel", amounts("a", 1), amounts("fuel", 1)),
            recipe("xy", amounts("x", 1, "fuel", 1), amounts("y", 1)),
            recipe("yx", amounts("y", 1), amounts("x", 2)));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(amounts("a", 1, "x", 1), result.extracted);
    }

    @Test
    void exactRingSupportsExternalInputsAndByproducts() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            1000,
            amounts("a", 2, "ore", 10000),
            recipe("ab", amounts("a", 2, "ore", 1), amounts("b", 3, "slag", 1)),
            recipe("bc", amounts("b", 3), amounts("c", 4)),
            recipe("ca", amounts("c", 4), amounts("a", 3)));
        assertEquals(Status.SUCCESS, result.status);
        assertTrue(result.extracted.get("ore") > 0);
    }

    @Test
    void threeStageRingReplaysLargeIntegerBalanceFromEverySeedPosition() {
        for (String seed : Arrays.asList("a", "b", "c")) {
            ECOPlanningResult<String, String> result = plan(
                "a",
                1_000_000_000L,
                amounts(seed, 4),
                recipe("ab", amounts("a", 2), amounts("b", 3)),
                recipe("bc", amounts("b", 3), amounts("c", 4)),
                recipe("ca", amounts("c", 4), amounts("a", 3)));
            assertEquals(Status.SUCCESS, result.status);
            assertTrue(result.schedule.size() < 300);
            Map<String, Long> material = new LinkedHashMap<>(result.extracted);
            for (ECOPlanningResult.Step<String, String> step : result.schedule) {
                step.recipe.inputs.forEach((key, value) -> {
                    long required = Math.multiplyExact(value, step.crafts);
                    assertTrue(material.getOrDefault(key, 0L) >= required);
                    material.put(key, material.get(key) - required);
                });
                step.recipe.outputs.forEach(
                    (key, value) -> material.merge(key, Math.multiplyExact(value, step.crafts), Math::addExact));
            }
            assertTrue(material.get("a") >= 1_000_000_000L);
        }
    }

    @Test
    void threeStageIntegerBalanceCannotInventStartupSeed() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            1_000_000_000L,
            Collections.emptyMap(),
            recipe("ab", amounts("a", 2), amounts("b", 3)),
            recipe("bc", amounts("b", 3), amounts("c", 4)),
            recipe("ca", amounts("c", 4), amounts("a", 3)));
        assertEquals(Status.CYCLE_UNRESOLVED, result.status);
        assertTrue(result.schedule.isEmpty());
        assertTrue(result.extracted.isEmpty());
    }

    @Test
    void billionItemRingUsesCompactSafeWaves() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            1_000_000_000L,
            amounts("a", 1),
            recipe("ab", amounts("a", 1), amounts("b", 1)),
            recipe("ba", amounts("b", 1), amounts("a", 2)));
        assertEquals(Status.SUCCESS, result.status);
        assertTrue(result.schedule.size() < 100);
        assertEquals(amounts("a", 1), result.extracted);
        Map<String, Long> material = new LinkedHashMap<>(result.extracted);
        for (ECOPlanningResult.Step<String, String> step : result.schedule) {
            step.recipe.inputs.forEach((key, value) -> {
                long used = Math.multiplyExact(value, step.crafts);
                assertTrue(material.getOrDefault(key, 0L) >= used);
                material.put(key, material.get(key) - used);
            });
            step.recipe.outputs
                .forEach((key, value) -> material.merge(key, Math.multiplyExact(value, step.crafts), Math::addExact));
        }
        assertTrue(material.get("a") >= 1_000_000_000L);
    }

    @Test
    void interleavedCycleCanTemporarilyConsumeGoalStock() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            3,
            amounts("a", 1),
            recipe("split", amounts("a", 1), amounts("b", 1, "c", 1)),
            recipe("return", amounts("b", 1), amounts("a", 1)),
            recipe("grow", amounts("a", 1, "c", 2), amounts("a", 3)));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(amounts("a", 1), result.extracted);
        assertEquals(5, result.schedule.size());
    }

    @Test
    void deadCycleSearchTerminatesWithoutInventingSeed() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            2,
            amounts("b", 1),
            recipe("ab", amounts("a", 1), amounts("b", 1)),
            recipe("ba", amounts("b", 1), amounts("a", 1)));
        assertEquals(Status.CYCLE_UNRESOLVED, result.status);
        assertTrue(result.schedule.isEmpty());
    }

    @Test
    void additionalRequestKeepsExistingGoalAsSeed() {
        ECORecipe<String, String> growth = recipe("grow", amounts("a", 9), amounts("a", 10));
        ECOPlanningResult<String, String> result = new ECOPlanningEngine<>(Arrays.asList(growth), () -> false, 32768)
            .planAdditional("a", 10, amounts("a", 9));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(10L, result.schedule.get(0).crafts);
        assertEquals(amounts("a", 9), result.extracted);
    }

    @Test
    void existingGoalDoesNotReplaceAdditionalCrafts() {
        ECORecipe<String, String> craft = recipe("craft", amounts("ore", 1), amounts("a", 1));
        ECOPlanningResult<String, String> result = new ECOPlanningEngine<>(Arrays.asList(craft), () -> false, 32768)
            .planAdditional("a", 3, amounts("a", 10, "ore", 3));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(3L, result.schedule.get(0).crafts);
    }

    @Test
    void plansLargeSeededGrowthAsOneSequentialPhase() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            1_000_000_009L,
            amounts("a", 9L, "ore", 1_000_000_000L),
            recipe("grow", amounts("a", 9L, "ore", 1L), amounts("a", 10L)));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(1, result.schedule.size());
        assertTrue(result.schedule.get(0).sequential);
        assertEquals(1_000_000_000L, result.schedule.get(0).crafts);
        assertEquals(amounts("a", 9L, "ore", 1_000_000_000L), result.extracted);
    }

    private static Map<String, Long> amounts(Object... pairs) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
        return map;
    }

    private static ECORecipe<String, String> recipe(String id, Map<String, Long> inputs, Map<String, Long> outputs) {
        return new ECORecipe<>(id, inputs, outputs);
    }

    @SafeVarargs
    private static ECOPlanningResult<String, String> plan(String goal, long count, Map<String, Long> stock,
        ECORecipe<String, String>... recipes) {
        return new ECOPlanningEngine<>(Arrays.asList(recipes), () -> false, 32768).plan(goal, count, stock);
    }

    @Test
    void sharesByproductsBetweenDependencies() {
        ECOPlanningResult<String, String> result = plan(
            "machine",
            1,
            amounts("ore", 1),
            recipe("smelt", amounts("ore", 1), amounts("iron", 2, "slag", 1)),
            recipe("assemble", amounts("iron", 2, "slag", 1), amounts("machine", 1)));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(amounts("ore", 1), result.extracted);
        assertEquals(2, result.schedule.size());
    }

    @Test
    void rollsBackFailedAlternativeIncludingItsProducedByproducts() {
        ECOPlanningResult<String, String> result = plan(
            "goal",
            1,
            amounts("ore", 1),
            recipe("bad", amounts("ore", 1, "unobtainable", 1), amounts("goal", 1)),
            recipe("good", amounts("ore", 1), amounts("goal", 1)));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(1, result.schedule.size());
        assertEquals("good", result.schedule.get(0).recipe.token);
        assertEquals(amounts("ore", 1), result.extracted);
    }

    @Test
    void seededSelfGrowthProducesOnlyFromRealSeed() {
        ECORecipe<String, String> growth = recipe("grow", amounts("seed", 1), amounts("seed", 2));
        ECOPlanningResult<String, String> result = plan("seed", 1024, amounts("seed", 1), growth);
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(amounts("seed", 1), result.extracted);
        assertEquals(1, result.schedule.size());
        assertEquals(1023L, result.schedule.get(0).crafts);
        assertTrue(result.schedule.get(0).sequential);
        assertEquals(Status.CYCLE_UNRESOLVED, plan("seed", 10, Collections.emptyMap(), growth).status);
    }

    @Test
    void growthReservesSeedWhileChoosingExternalMaterialRoute() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            2,
            amounts("a", 1, "ore", 1),
            recipe("grow", amounts("a", 1, "fuel", 1), amounts("a", 2)),
            recipe("burnSeed", amounts("a", 1), amounts("fuel", 1)),
            recipe("burnOre", amounts("ore", 1), amounts("fuel", 1)));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals("burnOre", result.schedule.get(0).recipe.token);
        assertEquals(amounts("a", 1, "ore", 1), result.extracted);
    }

    @Test
    void growthRejectsUnrepresentableGrossOutput() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            Long.MAX_VALUE / 2 + 2,
            amounts("a", 1),
            recipe("grow", amounts("a", 1), amounts("a", 2)));
        assertEquals(Status.AMOUNT_OVERFLOW, result.status);
        assertTrue(result.schedule.isEmpty());
        assertTrue(result.extracted.isEmpty());
    }

    @Test
    void multiPatternCycleRequiresAndReusesSeed() {
        ECOPlanningResult<String, String> result = plan(
            "a",
            5,
            amounts("a", 1),
            recipe("ab", amounts("a", 1), amounts("b", 1)),
            recipe("ba", amounts("b", 1), amounts("a", 2)));
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(amounts("a", 1), result.extracted);
        assertTrue(result.schedule.size() >= 2);
    }

    @Test
    void cycleWithoutGrowthCannotSatisfyMoreDemand() {
        assertEquals(
            Status.CYCLE_UNRESOLVED,
            plan(
                "a",
                2,
                amounts("a", 1),
                recipe("ab", amounts("a", 1), amounts("b", 1)),
                recipe("ba", amounts("b", 1), amounts("a", 1))).status);
    }

    @Test
    void overflowDoesNotPublishExecutableTasks() {
        ECOPlanningResult<String, String> result = plan(
            "goal",
            Long.MAX_VALUE,
            amounts("ore", Long.MAX_VALUE),
            recipe("overflow", amounts("ore", 2), amounts("goal", 1)));
        assertEquals(Status.AMOUNT_OVERFLOW, result.status);
        assertTrue(result.schedule.isEmpty());
        assertTrue(result.extracted.isEmpty());
    }

    @Test
    void cancellationAndWorkBudgetAreExplicit() {
        ECORecipe<String, String> recipe = recipe("r", amounts("ore", 1), amounts("goal", 1));
        assertEquals(
            Status.CANCELLED,
            new ECOPlanningEngine<>(Arrays.asList(recipe), () -> true, 100).plan("goal", 1, amounts("ore", 1)).status);
        assertEquals(
            Status.LIMIT_EXCEEDED,
            new ECOPlanningEngine<>(Arrays.asList(recipe), () -> false, 1).plan("goal", 1, amounts("ore", 1)).status);
    }

    @Test
    void recipeAndResultDoNotExposeMutableInputMaps() {
        Map<String, Long> input = amounts("ore", 1);
        ECORecipe<String, String> recipe = recipe("r", input, amounts("goal", 1));
        input.clear();
        assertEquals(amounts("ore", 1), recipe.inputs);
        Map<String, Long> stock = amounts("ore", 1);
        ECOPlanningResult<String, String> result = plan("goal", 1, stock, recipe);
        stock.clear();
        assertEquals(amounts("ore", 1), result.extracted);
    }
}
