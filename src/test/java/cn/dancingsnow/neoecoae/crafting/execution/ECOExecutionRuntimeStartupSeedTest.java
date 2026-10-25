package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.platform.TestFluids;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ECOExecutionRuntimeStartupSeedTest {

    @BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap.initialize();
    }

    @Test
    void protectedSeedSnapshotTracksConsumptionAndPhaseRelease() {
        AEKey seed = AEFluidKey.of(TestFluids.WATER);
        var first = pattern(mock(AEKey.class));
        var second = pattern(mock(AEKey.class));
        var setup = runtime(first, second, seed);
        var runtime = setup.runtime();
        var firstCandidate = new ECOExecutionRuntime.DispatchCandidate(0, 0, first, 2L, true);
        var secondCandidate = new ECOExecutionRuntime.DispatchCandidate(1, 1, second, 2L, true);

        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 12L),
            runtime.protectedStartupSeed(null));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 7L),
            runtime.protectedStartupSeed(firstCandidate));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 5L),
            runtime.protectedStartupSeed(secondCandidate));
        // Dispatch asks for the same snapshot once per candidate, so it must not be rebuilt while nothing moved.
        assertSame(runtime.protectedStartupSeed(firstCandidate), runtime.protectedStartupSeed(firstCandidate));

        var inputs = new KeyCounter();
        inputs.add(seed, 1L);
        setup.progress(0)
            .accept(1L);
        runtime.onAccepted(firstCandidate, 1L, new KeyCounter[] { inputs });
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 11L),
            runtime.protectedStartupSeed(null));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 7L),
            runtime.protectedStartupSeed(firstCandidate));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 4L),
            runtime.protectedStartupSeed(secondCandidate));

        // The final step completes phase 0, which releases whatever seed it still owned.
        setup.progress(0)
            .accept(1L);
        runtime.onAccepted(firstCandidate, 1L, new KeyCounter[0]);
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 7L),
            runtime.protectedStartupSeed(null));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            runtime.protectedStartupSeed(secondCandidate));
    }

    @Test
    void duplicateInputsAreSummedAgainstUnprotectedInventory() {
        AEKey seed = AEFluidKey.of(TestFluids.WATER);
        AEKey other = AEFluidKey.of(TestFluids.LAVA);
        var first = pattern(mock(AEKey.class));
        var second = pattern(mock(AEKey.class));
        var runtime = runtime(first, second, seed).runtime();
        var firstCandidate = new ECOExecutionRuntime.DispatchCandidate(0, 0, first, 2L, true);
        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.list.add(seed, 10L);
        inventory.list.add(other, 1L);

        // Phase 1 protects 7 of the 10, so phase 0 may take 3 in total across duplicate entries.
        assertTrue(
            runtime.preservesStartupSeeds(
                firstCandidate,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(new GenericStack(seed, 2L), new GenericStack(other, 1L), new GenericStack(seed, 1L)),
                inventory));
        assertFalse(
            runtime.preservesStartupSeeds(
                firstCandidate,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(new GenericStack(seed, 2L), new GenericStack(seed, 2L)),
                inventory));
        assertFalse(
            runtime.preservesStartupSeeds(
                firstCandidate,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(new GenericStack(other, 1L), new GenericStack(other, 1L)),
                inventory));
    }

    @Test
    void dynamicCycleProtectsSingleSeedFromTerminalConsumer() {
        AEKey seed = AEFluidKey.of(TestFluids.WATER);
        AEKey product = AEFluidKey.of(TestFluids.LAVA);
        var consumer = pattern(seed, product, 1L);
        var producer = pattern(seed, seed, 2L);
        var tasks = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(task(0, consumer, 1L), task(1, producer, 1L));
        var phase = new ECOExecutionPlan.PhaseSpec(
            0,
            0,
            ECOExecutionSchedule.Type.DYNAMIC_CYCLE,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0, 1),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(0, 1L, 1, 1L),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 1L));
        var plan = new ECOExecutionPlan(
            new PlanIdentity.Signature(
                product,
                1,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf()),
            ExecutionMode.DYNAMIC_CYCLE,
            tasks,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(phase),
            new ECOExecutionSchedule(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var progress = new ECOExecutionTaskProgress[] { new ECOExecutionTaskProgress(),
            new ECOExecutionTaskProgress() };
        progress[0].setExact(BigInteger.ONE, BigInteger.ONE);
        progress[1].setExact(BigInteger.ONE, BigInteger.ONE);
        var runtime = new ECOExecutionRuntime(
            plan,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(0, consumer, 1, producer),
            progress);

        var candidates = runtime.candidates();
        assertEquals(
            0,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(candidates)
                .taskId(),
            "fixture must put the consumer first");
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 1L),
            runtime.protectedStartupSeed(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(candidates)));
        assertEquals(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            runtime.protectedStartupSeed(candidates.get(1)));

        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(seed, 1L, Actionable.MODULATE);
        var blocked = new ECOCraftingInputPreview(
            inventory,
            consumer,
            runtime.protectedStartupSeed(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.first(candidates)),
            new ECOCraftingRemainderCache());
        assertNull(
            ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
                consumer,
                blocked,
                null,
                new KeyCounter(),
                new KeyCounter(),
                new ECOCraftingRemainderCache()));
    }

    @com.github.bsideup.jabel.Desugar
    private record Setup(ECOExecutionRuntime runtime, ECOExecutionTaskProgress[] progress) {

        ECOExecutionTaskProgress progress(int taskId) {
            return progress[taskId];
        }
    }

    private static Setup runtime(IPatternDetails first, IPatternDetails second, AEKey seed) {
        var tasks = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(task(0, first), task(1, second));
        var phases = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
            new ECOExecutionPlan.PhaseSpec(
                0,
                0,
                ECOExecutionSchedule.Type.CYCLE,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(0),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new ECOExecutionPlan.ExecutionStep(0, 2)),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 5L)),
            new ECOExecutionPlan.PhaseSpec(
                1,
                1,
                ECOExecutionSchedule.Type.CYCLE,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(1),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new ECOExecutionPlan.ExecutionStep(1, 2)),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(seed, 7L)));
        var signature = new PlanIdentity.Signature(
            mock(AEKey.class),
            1,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        var plan = new ECOExecutionPlan(
            signature,
            ExecutionMode.ORDERED_CYCLE,
            tasks,
            phases,
            new ECOExecutionSchedule(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        var progress = new ECOExecutionTaskProgress[2];
        for (int taskId = 0; taskId < progress.length; taskId++) {
            progress[taskId] = new ECOExecutionTaskProgress();
            progress[taskId].setExact(BigInteger.valueOf(2L), BigInteger.valueOf(2L));
        }
        return new Setup(
            new ECOExecutionRuntime(
                plan,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(0, first, 1, second),
                progress),
            progress);
    }

    private static ECOExecutionPlan.TaskSpec task(int id, IPatternDetails pattern, long count) {
        return new ECOExecutionPlan.TaskSpec(
            id,
            PlanIdentity.patternIdentityFor(pattern),
            pattern,
            ECOExecutionPlan.PatternRuntimeInfo.from(pattern),
            count,
            0,
            ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC);
    }

    private static ECOExecutionPlan.TaskSpec task(int id, IPatternDetails pattern) {
        return new ECOExecutionPlan.TaskSpec(
            id,
            PlanIdentity.patternIdentityFor(pattern),
            pattern,
            ECOExecutionPlan.PatternRuntimeInfo.from(pattern),
            Long.MAX_VALUE,
            id,
            ECOExecutionPlan.TaskKind.DAG);
    }

    private static IPatternDetails pattern(AEKey output) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(output, 1)));
        return pattern;
    }

    private static IPatternDetails pattern(AEKey inputKey, AEKey outputKey, long outputAmount) {
        var pattern = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(inputKey, 1) });
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(any(), any())).thenReturn(true);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
        when(pattern.getOutputs()).thenReturn(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(outputKey, outputAmount)));
        return pattern;
    }
}
