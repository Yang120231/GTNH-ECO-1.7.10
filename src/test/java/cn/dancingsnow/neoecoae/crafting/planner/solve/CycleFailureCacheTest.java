package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveRequest;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

class CycleFailureCacheTest {

    private final AEKey a = mock(AEKey.class, "A");
    private final AEKey fuel = mock(AEKey.class, "fuel");

    @Test
    void reuseIgnoresComponentRenumberingButNotStockTargetsOrBoundary() throws Exception {
        var calls = new AtomicInteger();
        CycleSolver solver = (request, cancellation) -> {
            calls.incrementAndGet();
            return failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT);
        };
        var cache = new CycleFailureCache(solver);
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        var reused = cache.solve(request(7, 2, 0, false), ECOCancellation.NONE);
        assertEquals(1, calls.get());
        assertTrue(
            reused.diagnostics()
                .stream()
                .anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.PROVEN_FAILURE_REUSED));
        cache.solve(request(0, 3, 0, false), ECOCancellation.NONE);
        cache.solve(request(0, 2, 1, false), ECOCancellation.NONE);
        cache.solve(request(0, 2, 0, true), ECOCancellation.NONE);
        assertEquals(4, calls.get());
        new CycleFailureCache(solver).solve(request(0, 2, 0, false), ECOCancellation.NONE);
        assertEquals(5, calls.get(), "Proofs must not survive a planning invocation");
    }

    @Test
    void budgetExhaustionIsNeverAnImpossibilityProof() throws Exception {
        var calls = new AtomicInteger();
        var cache = new CycleFailureCache((request, cancellation) -> {
            calls.incrementAndGet();
            return failure(CycleSolveStatus.UNKNOWN_BUDGET);
        });
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        assertEquals(2, calls.get());
    }

    @Test
    void impossibilityUnderImportCapsIsNotReusedWithoutThem() throws Exception {
        var calls = new AtomicInteger();
        var cache = new CycleFailureCache((request, cancellation) -> {
            calls.incrementAndGet();
            return failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT);
        });
        var capped = request(0, 2, 0, true)
            .withBoundaryImportCaps(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(fuel, PlannerAmount.ZERO));
        cache.solve(capped, ECOCancellation.NONE);
        cache.solve(capped, ECOCancellation.NONE);
        assertEquals(1, calls.get());
        cache.solve(request(0, 2, 0, true), ECOCancellation.NONE);
        assertEquals(2, calls.get());
        cache.solve(
            request(0, 2, 0, true).withBoundaryImportCaps(
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(fuel, PlannerAmount.of(1L))),
            ECOCancellation.NONE);
        assertEquals(3, calls.get());
    }

    @Test
    void cacheHitStillHonorsCancellation() throws Exception {
        var cache = new CycleFailureCache(
            (request, cancellation) -> failure(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT));
        cache.solve(request(0, 2, 0, false), ECOCancellation.NONE);
        assertThrows(
            InterruptedException.class,
            () -> cache.solve(request(0, 2, 0, false), () -> { throw new InterruptedException("cancelled"); }));
    }

    private CycleSolveRequest request(int id, long target, long stock, boolean boundary) {
        var edges = boundary
            ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                new ComponentDependency(
                    id,
                    id + 1,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new CraftingGraphEdge(a, fuel, null, null))))
            : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.<ComponentDependency>listOf();
        var component = new CycleComponent(
            id,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(a),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            edges);
        return new CycleSolveRequest(
            component,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, target),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(a, stock),
            edges,
            new CycleSolveRequest.PlannerOptions());
    }

    private static CycleSolveResult failure(CycleSolveStatus status) {
        return CycleSolveResult.failure(
            status,
            status == CycleSolveStatus.UNKNOWN_BUDGET ? CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED
                : CycleSolveDiagnostic.Code.PROVEN_INFEASIBLE_AT_CURRENT_STOCK,
            "test result");
    }
}
