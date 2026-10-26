package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItemRegistry;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ExecutingCraftingJobPersistenceTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void fullJobRoundTripsBoundProgressWaitingOwnershipAndRuntime() throws Exception {
        var fixture = fixture();
        var job = fixture.job;
        job.tasks.get(fixture.pattern)
            .accept(3);
        job.executionRuntime.onAccepted(
            job.executionRuntime.candidates()
                .get(0),
            3,
            new KeyCounter[0]);
        job.remainingAmount = 7;
        job.waitingFor.insert(AEItemKey.of(TestItems.IRON_INGOT), 3, appeng.api.config.Actionable.MODULATE);
        job.deferredStock.put(AEItemKey.of(TestItems.RAW_IRON), BigInteger.TEN.pow(25));
        job.batchedOutputs.add(AEItemKey.of(TestItems.IRON_INGOT));

        var restored = new ExecutingCraftingJob(
            job.writeToNBT(TestItemRegistry.CONTEXT),
            TestItemRegistry.CONTEXT,
            key -> {},
            fixture.context);
        assertEquals(job.link.getCraftingID(), restored.link.getCraftingID());
        assertEquals(7, restored.remainingAmount);
        assertEquals(7, restored.tasks.get(fixture.pattern).value);
        assertEquals(3, restored.waitingFor.list.get(AEItemKey.of(TestItems.IRON_INGOT)));
        assertEquals(job.deferredStock, restored.deferredStock);
        assertEquals(job.batchedOutputs, restored.batchedOutputs);
        assertNotNull(restored.executionRuntime);
        assertFalse(restored.suspended);
        assertEquals(
            7,
            restored.executionRuntime.candidates()
                .get(0)
                .maxDispatchCount());
    }

    @Test
    void lostPatternQuarantinesThePersistedExecutionContract() throws Exception {
        var fixture = fixture();
        var data = fixture.job.writeToNBT(TestItemRegistry.CONTEXT);
        when(fixture.context.decodePattern(any())).thenReturn(null);
        var restored = new ExecutingCraftingJob(data, TestItemRegistry.CONTEXT, key -> {}, fixture.context);
        assertTrue(restored.suspended);
        assertEquals("EXECUTION_METADATA_LOST", restored.permanentExecutionError);
        assertNull(restored.executionRuntime);
        assertEquals(10, restored.remainingAmount);
        assertEquals(fixture.job.link.getCraftingID(), restored.link.getCraftingID());
    }

    @Test
    void malformedPersistedRuntimeCannotFallBackToUnorderedExecution() throws Exception {
        var fixture = fixture();
        var data = fixture.job.writeToNBT(TestItemRegistry.CONTEXT);
        data.getCompound("executionPlan")
            .putString("mode", "INVALID_MODE");
        var restored = new ExecutingCraftingJob(data, TestItemRegistry.CONTEXT, key -> {}, fixture.context);
        assertTrue(restored.suspended);
        assertEquals("EXECUTION_METADATA_LOST", restored.permanentExecutionError);
        assertNull(restored.executionRuntime);
        assertEquals(10, restored.tasks.get(fixture.pattern).value);
    }

    private static Fixture fixture() throws Exception {
        var raw = AEItemKey.of(TestItems.RAW_IRON);
        var goal = AEItemKey.of(TestItems.IRON_INGOT);
        var pattern = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(raw, 1) });
        when(input.getMultiplier()).thenReturn(1L);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(TestItems.PAPER));
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
        when(pattern.getOutputs()).thenReturn(Collections.singletonList(new GenericStack(goal, 1)));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(goal, 1));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(goal)).thenReturn(Collections.singletonList(pattern));
        when(service.getCraftingFor(raw)).thenReturn(Collections.emptyList());
        var inventory = new KeyCounter();
        inventory.add(raw, 10);
        var result = new ECOCraftingPlannerService().createSession(service, goal, inventory)
            .plan(10, false, ECOCancellation.NONE);
        assertNotNull(result.executionContract());
        var context = mock(CraftingJobPersistenceContext.class);
        var nativeCpu = mock(appeng.api.networking.crafting.ICraftingCPU.class);
        when(context.nativeCPU()).thenReturn(nativeCpu);
        when(context.decodePattern(any())).thenReturn(pattern);
        var link = new CraftingLink(CraftingLink.generateData(UUID.randomUUID(), true, false), nativeCpu);
        var job = new ExecutingCraftingJob(
            result.plan(),
            result.executionContract()
                .executionPlan(),
            key -> {},
            link,
            12);
        return new Fixture(job, pattern, context);
    }

    private static final class Fixture {

        final ExecutingCraftingJob job;
        final IPatternDetails pattern;
        final CraftingJobPersistenceContext context;

        Fixture(ExecutingCraftingJob job, IPatternDetails pattern, CraftingJobPersistenceContext context) {
            this.job = job;
            this.pattern = pattern;
            this.context = context;
        }
    }
}
