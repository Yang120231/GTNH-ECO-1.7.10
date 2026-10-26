package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.forge.PlanningLifecycle;

class ECOPlanningLifecycleTest {

    @AfterEach
    void restartForOtherPlanningTests() {
        PlanningLifecycle.started();
    }

    @Test
    void stopCancelsRunningAndQueuedOptionalTasksAndRejectsLatePublication() throws Exception {
        PlanningLifecycle.started();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Future<?> active = ECOPlanningExecutor.tryImprove(() -> {
            running.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException stop) {
                interrupted.countDown();
                throw stop;
            }
            return null;
        });
        assertNotNull(active);
        assertTrue(running.await(2, TimeUnit.SECONDS));
        Future<?> queued = ECOPlanningExecutor.tryImprove(() -> fail("Old-world queued work ran"));
        assertNotNull(queued);
        PlanningLifecycle.stopped();
        assertTrue(active.isCancelled());
        assertTrue(queued.isCancelled());
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertNull(ECOPlanningExecutor.tryImprove(() -> null));
        assertThrows(RejectedExecutionException.class, () -> ECOPlanningExecutor.submit(() -> null));
    }

    @Test
    void aSecondWorldGetsFreshExecutorsAndContractGenerationInTheSameJvm() throws Exception {
        PlanningLifecycle.started();
        long firstGeneration = PatternRuntime.reloadGeneration();
        assertEquals(
            1,
            ECOPlanningExecutor.submit(() -> 1)
                .get(2, TimeUnit.SECONDS));
        PlanningLifecycle.stopped();
        assertTrue(PatternRuntime.reloadGeneration() > firstGeneration);
        PlanningLifecycle.started();
        assertEquals(
            2,
            ECOPlanningExecutor.submit(() -> 2)
                .get(2, TimeUnit.SECONDS));
        Future<Integer> optional = ECOPlanningExecutor.tryImprove(() -> 3);
        assertNotNull(optional);
        assertEquals(3, optional.get(2, TimeUnit.SECONDS));
    }
}
