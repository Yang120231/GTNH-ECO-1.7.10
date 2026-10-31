package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProcessingExecutionPattern;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.AEProcessingPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

/**
 * The processing budget counts provider submissions, not crafts. Counted targets receive whatever the task,
 * materials and energy allow in one submission; probing targets keep their per-visit and per-tick call limits.
 */
class ECOProcessingSubmissionBudgetTest {

    private static final String GT_HATCH = "gregtech.common.tileentities.machines.MTEHatchCraftingInputME";
    private static final double ONE_POWER = 2.0;
    private static final long HATCH_PUSH_LIMIT = NativeCraftingProvider.UNBOUNDED_HATCH_STACKS_PER_PUSH
        * Integer.MAX_VALUE;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void countedHatchAcceptsMoreThanIntegerMaxInOneSubmissionWithExactAccounting() throws Exception {
        long count = 5_000_000_000L;
        var h = new Harness(count, count);
        var provider = h.hatch();
        var result = h.dispatch(0, count, provider);

        assertNotNull(result);
        assertEquals(count, result.acceptedCrafts());
        assertEquals(JdkCompat.listOf(count), h.pushes(0));
        assertEquals(0L, h.inventory.list.get(h.inputs[0]));
        assertEquals(count, h.job.waitingFor.list.get(h.outputs[0]));
        assertEquals(0L, h.job.tasks.get(h.patterns[0]).value);
        assertEquals(ONE_POWER * count, h.consumedPower(), 1e-3);
    }

    @Test
    void oneMillionCraftsNoLongerCapsAPatternOrTheNextPattern() throws Exception {
        long count = 3_000_000L;
        var h = new Harness(count, count);
        var provider = h.hatch();
        assertEquals(
            count,
            h.dispatch(0, count, provider)
                .acceptedCrafts());
        // Same tick: the second ready task is not starved by the first one's huge batch.
        assertEquals(
            count,
            h.dispatch(1, count, provider)
                .acceptedCrafts());
        assertEquals(JdkCompat.listOf(count), h.pushes(0));
        assertEquals(JdkCompat.listOf(count), h.pushes(1));
    }

    @Test
    void hatchStackRepresentationBoundsOneSubmissionAndResumesNextTick() throws Exception {
        long count = 3 * HATCH_PUSH_LIMIT + 7;
        var h = new Harness(count, count);
        var provider = h.hatch();
        long total = 0;
        for (int tick = 1; tick <= 4; tick++) {
            h.dispatcher.beginTick(tick);
            var result = h.dispatch(0, count - total, provider);
            assertNotNull(result, "tick " + tick);
            total += result.acceptedCrafts();
            // The remainder waits for the next tick instead of inflating the hatch further this tick.
            assertNull(h.dispatch(0, count - total, provider));
        }
        assertEquals(JdkCompat.listOf(HATCH_PUSH_LIMIT, HATCH_PUSH_LIMIT, HATCH_PUSH_LIMIT, 7L), h.pushes(0));
        assertEquals(count, total);
        assertEquals(0L, h.inventory.list.get(h.inputs[0]));
        assertEquals(count, h.job.waitingFor.list.get(h.outputs[0]));
        assertEquals(0L, h.job.tasks.get(h.patterns[0]).value);
    }

    @Test
    void probingTargetsKeepPerPatternAndPerTickSubmissionLimits() throws Exception {
        var h = new Harness(1L << 40, 1L << 40, 1L << 40);
        var provider = h.probingTarget();
        int[] submissions = new int[3];
        for (int id = 0; id < 3; id++) {
            while (h.dispatch(id, 1L << 40, provider) != null) {
                assertTrue(++submissions[id] <= 64, "visits must stay bounded");
            }
        }
        assertEquals(
            ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK,
            h.pushes(0)
                .size());
        assertEquals(
            ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK,
            h.pushes(1)
                .size());
        assertEquals(
            0,
            h.pushes(2)
                .size(),
            "the tick's submissions are spent");
        // Probing still grows from one copy and never jumps to the requested total.
        assertEquals(
            1L,
            h.pushes(0)
                .get(0));
        assertTrue(
            h.pushes(0)
                .stream()
                .allMatch(copies -> copies <= Integer.MAX_VALUE));
        assertTrue(h.dispatcher.isScaledAttemptBudgetExhausted());

        h.dispatcher.beginTick(2);
        assertFalse(h.dispatcher.isScaledAttemptBudgetExhausted(h.patterns[2]));
        assertNotNull(h.dispatch(2, 1L << 40, provider));
        assertEquals(
            1L,
            h.pushes(2)
                .get(0));
    }

    @Test
    void budgetExhaustionIsNotRecordedAsAProviderFailure() throws Exception {
        var h = new Harness(1L << 40, 1L << 40);
        var provider = h.probingTarget();
        while (h.dispatch(0, 1L << 40, provider) != null) {
            // spend pattern 0's share
        }
        var marks = new int[1];
        assertNull(
            h.dispatcher.tryScaledDispatch(
                h.request(0, 1L << 40),
                provider,
                ONE_POWER,
                h.energy,
                ignored -> marks[0]++,
                h::push));
        assertEquals(0, marks[0], "a paused pattern must not mark a provider attempt");
        assertFalse(h.dispatcher.isScaledDispatchDeferred(h.request(0, 1), provider));
        assertFalse(h.dispatcher.isScaledFallbackDeferred(h.request(0, 1), provider));
        assertFalse(h.dispatcher.claimFallbackAttempt(h.patterns[0]));
        assertTrue(h.dispatcher.claimFallbackAttempt(h.patterns[1]));
        assertFalse(h.dispatcher.mayAttemptScaled(provider, h.patterns[0]));
        assertTrue(h.dispatcher.mayAttemptScaled(provider, h.patterns[1]));
    }

    @Test
    void rejectedSubmissionRefundsMaterialsAndEnergy() throws Exception {
        long count = 4_000_000_000L;
        var h = new Harness(count, count);
        var provider = h.hatch();
        h.admission = copies -> false;
        assertNull(h.dispatch(0, count, provider));
        assertEquals(count, h.inventory.list.get(h.inputs[0]));
        assertEquals(0L, h.job.waitingFor.list.get(h.outputs[0]));
        assertEquals(count, h.job.tasks.get(h.patterns[0]).value);
        assertEquals(0.0, h.consumedPower(), 1e-6);
        assertFalse(h.job.suspended);
    }

    @Test
    void uncertainOwnershipKeepsTheDebitAndNeverReplays() throws Exception {
        long count = 4_000_000_000L;
        var h = new Harness(count, count);
        var provider = h.hatch();
        h.admission = copies -> { throw new IllegalStateException("target threw after taking ownership"); };
        assertNull(h.dispatch(0, count, provider));
        assertTrue(h.job.suspended);
        assertEquals("AMBIGUOUS_SCALED_PROVIDER_OWNERSHIP", h.job.permanentExecutionError);
        assertEquals(0L, h.inventory.list.get(h.inputs[0]), "inputs may be inside the target: no refund");
        assertEquals(1, h.attemptedPushes);
    }

    @Test
    void bufferedAcceptanceOwnsTheChunkButStopsTheVisit() throws Exception {
        var h = new Harness(1_000, 1_000);
        var provider = h.probingTarget();
        doReturn(false).when(provider)
            .isScaledProcessingFullyInserted();
        var result = h.dispatch(0, 1_000, provider);
        assertEquals(1L, result.acceptedCrafts());
        assertEquals(1L, h.job.waitingFor.list.get(h.outputs[0]));
        assertTrue(h.dispatcher.isScaledDispatchDeferred(h.request(0, 1), provider));
        assertTrue(h.dispatcher.isScaledFallbackDeferred(h.request(0, 1), provider));
        h.dispatcher.beginTick(2);
        assertFalse(h.dispatcher.isScaledDispatchDeferred(h.request(0, 1), provider));
    }

    @Test
    void materialsAndEnergyLimitTheCountedBatch() throws Exception {
        long count = 10_000_000_000L;
        var h = new Harness(count, count);
        h.inventory.extract(h.inputs[0], 3_000_000_000L, Actionable.MODULATE);
        var provider = h.hatch();
        assertEquals(
            7_000_000_000L,
            h.dispatch(0, count, provider)
                .acceptedCrafts());
        assertEquals(0L, h.inventory.list.get(h.inputs[0]));

        h.powerAvailable[0] = ONE_POWER * 1_234_567_890L;
        assertEquals(
            1_234_567_890L,
            h.dispatch(1, count, provider)
                .acceptedCrafts());
        assertEquals(count - 1_234_567_890L, h.inventory.list.get(h.inputs[1]));
        assertEquals(1_234_567_890L, h.job.waitingFor.list.get(h.outputs[1]));
    }

    @Test
    void protectedCycleSeedAndCycleBatchLimitAreNeverSent() throws Exception {
        long count = 5_000_000_000L;
        var h = new Harness(count, count);
        var runtime = mock(ECOExecutionRuntime.class);
        when(runtime.protectedStartupSeed(any())).thenReturn(JdkCompat.mapOf(h.inputs[0], 1_000_000_000L));
        when(runtime.limitCycleBatch(any(), any(), any(), anyLong()))
            .thenAnswer(call -> Math.min(call.<Long>getArgument(3), 3_500_000_000L));
        h.job.executionRuntime = runtime;
        var provider = h.hatch();
        // The cycle limit is consulted for every chunk; the seed caps the second one.
        assertEquals(
            4_000_000_000L,
            h.dispatch(0, count, provider)
                .acceptedCrafts());
        assertEquals(JdkCompat.listOf(3_500_000_000L, 500_000_000L), h.pushes(0));
        h.dispatcher.beginTick(2);
        assertNull(h.dispatch(0, count, provider));
        assertEquals(1_000_000_000L, h.inventory.list.get(h.inputs[0]), "the startup seed stays in the CPU");
    }

    @Test
    void smartDoublingOffKeepsTheCoProcessorRulePerSubmission() throws Exception {
        long perSubmission = ECOComputationSystemBlockEntity.smartDoublingDispatchLimit(false, 5);
        assertEquals(5L, perSubmission);
        var h = new Harness(1_000_000, 1_000_000);
        var provider = h.hatch();
        while (h.dispatch(0, perSubmission, provider) != null) {
            // The mode rule bounds each submission; the call budget bounds how many happen per tick.
        }
        assertEquals(
            ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK,
            h.pushes(0)
                .size());
        assertTrue(
            h.pushes(0)
                .stream()
                .allMatch(copies -> copies == perSubmission));
        assertEquals(Long.MAX_VALUE, ECOComputationSystemBlockEntity.smartDoublingDispatchLimit(true, 5));
    }

    @Test
    void resetDropsDeferralsAndSpentBudget() throws Exception {
        long count = 3 * HATCH_PUSH_LIMIT;
        var h = new Harness(count, 1L << 40);
        var hatch = h.hatch();
        assertNotNull(h.dispatch(0, count, hatch));
        assertTrue(h.dispatcher.isScaledDispatchDeferred(h.request(0, 1), hatch));
        var probing = h.probingTarget();
        while (h.dispatch(1, 1L << 40, probing) != null) {
            // spend pattern 1's share
        }
        assertTrue(h.dispatcher.isScaledAttemptBudgetExhausted(h.patterns[1]));
        h.dispatcher.reset();
        h.dispatcher.beginTick(1);
        assertFalse(h.dispatcher.isScaledDispatchDeferred(h.request(0, 1), hatch));
        assertFalse(h.dispatcher.isScaledAttemptBudgetExhausted(h.patterns[1]));
        assertEquals(
            HATCH_PUSH_LIMIT,
            h.dispatch(0, count, hatch)
                .acceptedCrafts());
    }

    @Test
    void hatchLimitIsPerResourceAndNeverBelowOneCopy() {
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(TestItems.STONE), 4);
        inputs.add(AEItemKey.of(TestItems.COBBLESTONE), 1);
        var again = new KeyCounter();
        again.add(AEItemKey.of(TestItems.STONE), 4);
        assertEquals(
            HATCH_PUSH_LIMIT / 8,
            NativeCraftingProvider.unboundedHatchCopyLimit(new KeyCounter[] { inputs, again }));
        var huge = new KeyCounter();
        huge.add(AEItemKey.of(TestItems.STONE), Long.MAX_VALUE);
        assertEquals(1L, NativeCraftingProvider.unboundedHatchCopyLimit(new KeyCounter[] { huge }));
    }

    /** Pattern {@code i} turns one {@code inputs[i]} into one {@code outputs[i]}. */
    private static final class Harness {

        final AEItemKey[] inputs = { AEItemKey.of(TestItems.STONE), AEItemKey.of(TestItems.COBBLESTONE),
            AEItemKey.of(TestItems.PAPER) };
        final AEItemKey[] outputs = { AEItemKey.of(TestItems.IRON_INGOT), AEItemKey.of(TestItems.GOLD_INGOT),
            AEItemKey.of(TestItems.BRICK) };
        final AEProcessingPattern[] patterns;
        final ListCraftingInventory inventory = new ListCraftingInventory(key -> {});
        final IEnergyGrid energy = mock(IEnergyGrid.class);
        final double[] powerAvailable = { Double.MAX_VALUE };
        double extracted;
        double injected;
        final ExecutingCraftingJob job;
        final ECOProcessingPatternDispatcher dispatcher;
        final List<List<Long>> pushes = new ArrayList<>();
        Predicate<Long> admission = copies -> true;
        int attemptedPushes;

        Harness(long... counts) {
            patterns = new AEProcessingPattern[counts.length];
            Map<IPatternDetails, Long> times = new java.util.LinkedHashMap<>();
            for (int i = 0; i < counts.length; i++) {
                patterns[i] = pattern(i);
                times.put(patterns[i], counts[i]);
                inventory.insert(inputs[i], counts[i], Actionable.MODULATE);
                pushes.add(new ArrayList<>());
            }
            when(energy.extractAEPower(anyDouble(), any(), eq(PowerMultiplier.CONFIG))).thenAnswer(call -> {
                double amount = Math.min(call.<Double>getArgument(0), powerAvailable[0]);
                if (call.getArgument(1) == Actionable.MODULATE) {
                    extracted += amount;
                    if (powerAvailable[0] != Double.MAX_VALUE) powerAvailable[0] -= amount;
                }
                return amount;
            });
            when(energy.injectPower(anyDouble(), any())).thenAnswer(call -> {
                if (call.getArgument(1) == Actionable.MODULATE) {
                    injected += call.<Double>getArgument(0);
                    if (powerAvailable[0] != Double.MAX_VALUE) powerAvailable[0] += call.<Double>getArgument(0);
                }
                return 0.0;
            });
            ICraftingPlan plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(outputs[0], counts[0]));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            when(plan.patternTimes()).thenReturn(times);
            var cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
            job = new ExecutingCraftingJob(
                plan,
                ignored -> {},
                new CraftingLink(
                    CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false),
                    cpu.getCluster()
                        .nativeCPU(cpu)),
                null);
            var accounting = new ECOCraftingDispatchAccounting(
                ignored -> {},
                () -> {},
                ignored -> mock(ECOCraftingJobContext.class),
                event -> {});
            dispatcher = new ECOProcessingPatternDispatcher(
                mock(ECOCraftingCPULogic.class),
                new ECOCraftingEnergyTransaction(() -> {}, () -> 1),
                accounting);
            dispatcher.beginTick(1);
        }

        private AEProcessingPattern pattern(int id) {
            var pattern = mock(AEProcessingPattern.class);
            IPatternDetails.IInput input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(inputs[id], 1) });
            when(input.getMultiplier()).thenReturn(1L);
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
            when(pattern.getOutputs()).thenReturn(JdkCompat.listOf(new GenericStack(outputs[id], 1)));
            when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(outputs[id], 1));
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(id == 0 ? TestItems.BRICK : TestItems.PAPER));
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            return pattern;
        }

        @SuppressWarnings("unchecked")
        NativeCraftingProvider hatch() throws ClassNotFoundException {
            var medium = (ICraftingMedium) mock(
                (Class<Object>) Class.forName(GT_HATCH, false, Harness.class.getClassLoader()));
            when(medium.getBlockingMode()).thenReturn(ICraftingMedium.BlockingMode.NONE);
            return new NativeCraftingProvider(medium, JdkCompat.listOf(patterns));
        }

        /** A bounded target ECO must probe: it accepts and drains every chunk immediately. */
        NativeCraftingProvider probingTarget() {
            ICraftingMedium medium = mock(ICraftingMedium.class);
            when(medium.getBlockingMode()).thenReturn(ICraftingMedium.BlockingMode.NONE);
            var provider = spy(new NativeCraftingProvider(medium, JdkCompat.listOf(patterns)));
            doReturn(true).when(provider)
                .supportsScaledProcessing();
            doReturn(true).when(provider)
                .isScaledProcessingFullyInserted();
            return provider;
        }

        ECOCraftingDispatchRequest request(int id, long allowed) {
            var in = new KeyCounter();
            in.add(inputs[id], 1);
            var out = new KeyCounter();
            out.add(outputs[id], 1);
            var candidate = job.executionRuntime == null ? null
                : new ECOExecutionRuntime.DispatchCandidate(id, 0, patterns[id], allowed, false);
            return new ECOCraftingDispatchRequest(
                job,
                candidate,
                patterns[id],
                new KeyCounter[] { in },
                out,
                new KeyCounter(),
                allowed,
                inventory,
                mock(net.minecraft.world.World.class));
        }

        ECOCraftingDispatchResult dispatch(int id, long allowed, NativeCraftingProvider provider) {
            return dispatcher
                .tryScaledDispatch(request(id, allowed), provider, ONE_POWER, energy, ignored -> {}, this::push);
        }

        boolean push(ECOCraftingDispatchRequest scaled,
            cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider target) {
            attemptedPushes++;
            // Registered-pattern identity reaches the provider; only the execution view is scaled.
            IPatternDetails original = scaled.pattern() instanceof ECOProcessingExecutionPattern
                ? ((ECOProcessingExecutionPattern) scaled.pattern()).providerLookupPattern()
                : scaled.pattern();
            int id = java.util.Arrays.asList(patterns)
                .indexOf(original);
            assertTrue(id >= 0);
            long copies = scaled.allowedCrafts();
            assertEquals(copies, scaled.inputs()[0].get(inputs[id]), "aggregate input equals copies x one-copy input");
            if (!admission.test(copies)) return false;
            pushes.get(id)
                .add(copies);
            return true;
        }

        List<Long> pushes(int id) {
            return pushes.get(id);
        }

        double consumedPower() {
            return extracted - injected;
        }
    }
}
