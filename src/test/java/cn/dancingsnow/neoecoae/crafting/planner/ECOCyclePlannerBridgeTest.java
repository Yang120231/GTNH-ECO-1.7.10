package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.ported.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.CycleSolveDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.CycleSolveStatus;

class ECOCyclePlannerBridgeTest {

    private static Map<String, Long> stock(String key, long amount) {
        return Collections.singletonMap(key, amount);
    }

    @Test
    void upstreamExactRingDiagnosticSurvivesBoundary() throws Exception {
        ECOCyclePlannerBridge<String, String> bridge = new ECOCyclePlannerBridge<>(
            "a",
            Arrays.asList(
                new ECORecipe<>("ab", stock("a", 2), stock("b", 3)),
                new ECORecipe<>("bc", stock("b", 3), stock("c", 4)),
                new ECORecipe<>("ca", stock("c", 4), stock("a", 3))),
            ECOCancellation.NONE);
        var result = bridge.solve("a", 1_000_000_000L, stock("a", 2), ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.SUCCESS, result.status());
        assertTrue(
            result.diagnostics()
                .stream()
                .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.DETERMINISTIC_RING_EXACT));
        assertTrue(
            result.executionPlan()
                .size() < 300);
    }

    @Test
    void seedLadderReportsShortfallWithoutClaimingExecutableSuccess() throws Exception {
        ECOCyclePlannerBridge<String, String> bridge = new ECOCyclePlannerBridge<>(
            "a",
            Arrays.asList(
                new ECORecipe<>("ab", stock("a", 1), stock("b", 1)),
                new ECORecipe<>("ba", stock("b", 1), stock("a", 2))),
            ECOCancellation.NONE);
        var result = bridge.solve("a", 10, Collections.emptyMap(), ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status());
        assertFalse(
            result.seedShortfall()
                .isEmpty());
        assertTrue(
            result.diagnostics()
                .stream()
                .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.SEED_LADDER_VERIFIED));
        Map<Object, Long> material = new LinkedHashMap<>(result.requiredSeed());
        for (var run : result.executionPlan()) {
            for (var input : run.pattern()
                .inputs()) {
                long count = Math.multiplyExact(
                    input.amountPerPattern()
                        .longValueExact(),
                    run.count());
                assertTrue(material.getOrDefault(input.key(), 0L) >= count);
                material.merge(input.key(), -count, Math::addExact);
            }
            for (var output : run.pattern()
                .outputs())
                material.merge(output.what(), Math.multiplyExact(output.amount(), run.count()), Math::addExact);
        }
        assertTrue(material.get("a") >= 10);
    }
}
