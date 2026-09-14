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
        assertEquals(10, result.schedule.size());
        assertEquals(Status.CYCLE_UNRESOLVED, plan("seed", 10, Collections.emptyMap(), growth).status);
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
