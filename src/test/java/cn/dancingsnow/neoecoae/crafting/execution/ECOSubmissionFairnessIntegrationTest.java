package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.AEProcessingPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;
import cn.dancingsnow.neoecoae.platform.minecraft.TickClock;

/** Runs the CPU scheduler, input resolution, provider transactions and ledger across a whole tick. */
class ECOSubmissionFairnessIntegrationTest {

    private static final String GT_HATCH = "gregtech.common.tileentities.machines.MTEHatchCraftingInputME";
    private MockedStatic<TickClock> ticks;
    private long tick;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @BeforeEach
    void setupTicks() {
        ticks = mockStatic(TickClock.class);
        ticks.when(TickClock::currentTick)
            .thenAnswer(call -> tick);
    }

    @AfterEach
    void closeTicks() {
        ticks.close();
    }

    @Test
    void aHugeCountedTaskLeavesTheSameTickToTheNextReadyTask() throws Exception {
        long count = 3_000_000_000L;
        var f = new Fixture(count, Target.HATCH, Target.HATCH);
        assertEquals(2, f.tick(64));
        assertEquals(JdkCompat.listOf(count), f.received.get(0));
        assertEquals(JdkCompat.listOf(count), f.received.get(1));
        for (int id = 0; id < 2; id++) {
            assertEquals(0L, f.logic.getInventory().list.get(f.inputs[id]));
            assertEquals(count, f.job.waitingFor.list.get(f.outputs[id]));
            assertEquals(0L, f.job.tasks.get(f.patterns[id]).value);
        }
        assertFalse(f.job.suspended, f.job.permanentExecutionError);
    }

    @Test
    void anExhaustedOrdinaryBudgetDoesNotHideACountedTarget() throws Exception {
        long count = 3_000_000_000L;
        var f = new Fixture(count, Target.ORDINARY, Target.HATCH);
        assertEquals(1, f.tick(0));
        assertEquals(JdkCompat.listOf(), f.received.get(0), "one-copy pushes still need the ordinary budget");
        assertEquals(JdkCompat.listOf(count), f.received.get(1));
        assertEquals(count, f.logic.getInventory().list.get(f.inputs[0]));
        assertEquals(0L, f.logic.getInventory().list.get(f.inputs[1]));
    }

    @Test
    void aContinuouslyAcceptingProbingPatternCannotTakeTheWholeTick() throws Exception {
        long count = 1L << 40;
        var f = new Fixture(count, Target.PROBING, Target.PROBING);
        f.tick(64);
        int share = ECOProcessingPatternDispatcher.MAX_ATTEMPTS_PER_PATTERN_PER_TICK;
        assertEquals(
            share,
            f.received.get(0)
                .size());
        assertEquals(
            share,
            f.received.get(1)
                .size());
        long sent0 = f.received.get(0)
            .stream()
            .mapToLong(Long::longValue)
            .sum();
        assertEquals(count - sent0, f.logic.getInventory().list.get(f.inputs[0]));
        assertEquals(sent0, f.job.waitingFor.list.get(f.outputs[0]));

        // The next tick resets the shares; the preferred pattern resumes first.
        f.tick(64);
        assertEquals(
            2 * share,
            f.received.get(0)
                .size());
        assertEquals(
            2 * share,
            f.received.get(1)
                .size());
    }

    private enum Target {
        HATCH,
        PROBING,
        ORDINARY
    }

    private final class Fixture {

        final AEItemKey[] inputs = { AEItemKey.of(TestItems.STONE), AEItemKey.of(TestItems.COBBLESTONE) };
        final AEItemKey[] outputs = { AEItemKey.of(TestItems.IRON_INGOT), AEItemKey.of(TestItems.GOLD_INGOT) };
        final AEProcessingPattern[] patterns = { pattern(0), pattern(1) };
        final IEnergyGrid energy = mock(IEnergyGrid.class);
        final ICraftingService service = mock(ICraftingService.class);
        final World level = mock(World.class);
        final List<List<Long>> received = JdkCompat.listOf(new ArrayList<>(), new ArrayList<>());
        final ECOCraftingCPULogic logic;
        final ExecutingCraftingJob job;

        Fixture(long count, Target... targets) throws ClassNotFoundException {
            var cpu = mock(ECOCraftingCPU.class);
            when(cpu.isActive()).thenReturn(true);
            when(cpu.getLevel()).thenReturn(level);
            logic = new ECOCraftingCPULogic(cpu);
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(outputs[1], count));
            when(plan.patternTimes()).thenReturn(JdkCompat.mapOf(patterns[0], count, patterns[1], count));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            var phase = new ECOExecutionPlan.PhaseSpec(
                0,
                0,
                ECOExecutionSchedule.Type.DAG,
                JdkCompat.listOf(0, 1),
                JdkCompat.listOf(),
                JdkCompat.listOf());
            var executionPlan = new ECOExecutionPlan(
                mock(PlanIdentity.Signature.class),
                ExecutionMode.PHASED_DAG,
                JdkCompat.listOf(task(0, count), task(1, count)),
                JdkCompat.listOf(phase),
                new ECOExecutionSchedule(JdkCompat.listOf()));
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(UUID.randomUUID());
            try (var tracker = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, executionPlan, ignored -> {}, link, null);
            }
            logic.setJobFromPersistence(job);
            for (int id = 0; id < 2; id++) {
                var provider = provider(id, targets[id]);
                when(service.getProviders(patterns[id])).thenReturn(JdkCompat.listOf(provider));
                logic.getInventory()
                    .insert(inputs[id], count, Actionable.MODULATE);
            }
            when(energy.extractAEPower(anyDouble(), any(), any(appeng.api.config.PowerMultiplier.class)))
                .thenAnswer(call -> call.getArgument(0));
        }

        @SuppressWarnings("unchecked")
        private ICraftingProvider provider(int id, Target target) throws ClassNotFoundException {
            if (target == Target.ORDINARY) {
                var provider = mock(ICraftingProvider.class);
                when(provider.pushPattern(any(), any())).thenAnswer(call -> receive(id, call.getArgument(1)));
                return provider;
            }
            ICraftingMedium medium = target == Target.HATCH
                ? (ICraftingMedium) mock((Class<Object>) Class.forName(GT_HATCH, false, getClass().getClassLoader()))
                : mock(ICraftingMedium.class);
            when(medium.getBlockingMode()).thenReturn(ICraftingMedium.BlockingMode.NONE);
            var provider = spy(new NativeCraftingProvider(medium, JdkCompat.listOf(patterns[id])));
            doAnswer(call -> receive(id, call.getArgument(1))).when(provider)
                .pushPattern(any(), any());
            if (target == Target.PROBING) {
                doReturn(true).when(provider)
                    .supportsScaledProcessing();
                doReturn(true).when(provider)
                    .isScaledProcessingFullyInserted();
            }
            return provider;
        }

        private boolean receive(int id, KeyCounter[] inputHolder) {
            received.get(id)
                .add(inputHolder[0].get(inputs[id]));
            return true;
        }

        private AEProcessingPattern pattern(int id) {
            var pattern = mock(AEProcessingPattern.class);
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(inputs[id], 1) });
            when(input.getMultiplier()).thenReturn(1L);
            when(input.isValid(eq(inputs[id]), any())).thenReturn(true);
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(id == 0 ? TestItems.PAPER : TestItems.BRICK));
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
            when(pattern.getOutputs())
                .thenReturn(java.util.Collections.singletonList(new GenericStack(outputs[id], 1)));
            when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(outputs[id], 1));
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            return pattern;
        }

        private ECOExecutionPlan.TaskSpec task(int id, long count) {
            return new ECOExecutionPlan.TaskSpec(
                id,
                mock(PlanIdentity.PatternIdentity.class),
                patterns[id],
                ECOExecutionPlan.PatternRuntimeInfo.from(patterns[id]),
                count,
                0,
                ECOExecutionPlan.TaskKind.DAG);
        }

        /** Mirrors the CPU tick: repeat passes while anything is accepted. Returns accepted submissions. */
        int tick(int ordinaryBudget) {
            tick++;
            int before = received.get(0)
                .size()
                + received.get(1)
                    .size();
            for (int pass = 0; pass < 10_000; pass++) {
                if (logic.executeNormalCrafting(ordinaryBudget, service, energy, level) == 0) break;
                assertTrue(pass < 9_999, "a tick must terminate");
            }
            return received.get(0)
                .size()
                + received.get(1)
                    .size()
                - before;
        }
    }
}
