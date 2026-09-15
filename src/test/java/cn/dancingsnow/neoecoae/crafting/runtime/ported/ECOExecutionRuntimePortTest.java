package cn.dancingsnow.neoecoae.crafting.runtime.ported;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ExecutionMode;

class ECOExecutionRuntimePortTest {

    private static int nextPattern;

    private static ICraftingPatternDetails pattern() {
        final int marker = nextPattern++;
        return (ICraftingPatternDetails) Proxy.newProxyInstance(
            ICraftingPatternDetails.class.getClassLoader(),
            new Class<?>[] { ICraftingPatternDetails.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName()
                    .equals("equals")) return proxy == args[0];
                if (method.getName()
                    .equals("getPattern")) {
                    var encoded = new net.minecraft.item.ItemStack((net.minecraft.item.Item) null);
                    var tag = new net.minecraft.nbt.NBTTagCompound();
                    tag.setInteger("marker", marker);
                    encoded.setTagCompound(tag);
                    return encoded;
                }
                if (method.getReturnType() == boolean.class) return false;
                if (method.getName()
                    .equals("getCondensedAEInputs")) return new IAEStack<?>[0];
                return null;
            });
    }

    @Test
    void upstreamScheduleKeepsIndependentPhysicalTasksInSeparatePhases() {
        var first = pattern();
        var second = pattern();
        var firstComponent = new cn.dancingsnow.neoecoae.crafting.planner.ported.result.ComponentPlanningResult(
            0,
            cn.dancingsnow.neoecoae.crafting.planner.ported.result.ComponentPlanningResult.Type.ACYCLIC,
            cn.dancingsnow.neoecoae.crafting.planner.ported.result.ComponentPlanningResult.Status.PLANNED,
            Collections.emptyMap(),
            Collections.singleton(first),
            null,
            null,
            Collections.emptyMap(),
            null,
            null);
        var secondComponent = new cn.dancingsnow.neoecoae.crafting.planner.ported.result.ComponentPlanningResult(
            1,
            cn.dancingsnow.neoecoae.crafting.planner.ported.result.ComponentPlanningResult.Type.ACYCLIC,
            cn.dancingsnow.neoecoae.crafting.planner.ported.result.ComponentPlanningResult.Status.PLANNED,
            Collections.emptyMap(),
            Collections.singleton(second),
            null,
            null,
            Collections.emptyMap(),
            null,
            null);
        Map<ICraftingPatternDetails, Long> tasks = new LinkedHashMap<>();
        tasks.put(first, 3L);
        tasks.put(second, 2L);
        var schedule = ECOExecutionSchedule
            .from(Arrays.asList(firstComponent, secondComponent), Arrays.asList(0, 1), tasks);
        assertEquals(
            2,
            schedule.phases()
                .size());
        assertTrue(
            schedule.dependencies()
                .isEmpty());
    }

    @Test
    void physicalGraphIncludesByproductAndReturnedSuppliers() {
        var producer = pattern();
        var consumer = pattern();
        var amount = cn.dancingsnow.neoecoae.crafting.planner.ported.solve.PlannerAmount.ONE;
        var produced = new cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics(
            producer,
            null,
            Collections.emptyList(),
            Collections
                .singletonList(new cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack("product", 1)),
            Collections
                .singletonList(new cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack("returned", 1)),
            Collections.emptyList(),
            cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics.MatchingMode.EXACT,
            cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        var consumed = new cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics(
            consumer,
            null,
            Arrays.asList(
                new cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics.Input(
                    null,
                    "product",
                    amount,
                    null,
                    amount),
                new cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics.Input(
                    null,
                    "returned",
                    amount,
                    null,
                    amount)),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics.MatchingMode.EXACT,
            cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        Map<ICraftingPatternDetails, cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics> selected = new LinkedHashMap<>();
        selected.put(producer, produced);
        selected.put(consumer, consumed);
        var graph = cn.dancingsnow.neoecoae.crafting.planner.ported.result.SelectedExecutionGraph.build(selected);
        assertEquals(
            2,
            graph.dependencies()
                .size());
        assertTrue(
            graph.dependencies()
                .stream()
                .allMatch(edge -> edge.producer() == producer && edge.consumer() == consumer));
    }

    @Test
    void fullPlanRoundTripRetainsDynamicSeedAllocationsAndDependencies() {
        ICraftingPatternDetails first = pattern();
        ICraftingPatternDetails second = pattern();
        ICraftingPatternDetails[] patterns = { first, second };
        ECOExecutionPlanCodec.Codec codec = new ECOExecutionPlanCodec.Codec() {

            public net.minecraft.nbt.NBTTagCompound writePattern(ICraftingPatternDetails value) {
                var tag = new net.minecraft.nbt.NBTTagCompound();
                tag.setInteger("id", value == first ? 0 : 1);
                return tag;
            }

            public ICraftingPatternDetails readPattern(net.minecraft.nbt.NBTTagCompound tag) {
                return patterns[tag.getInteger("id")];
            }

            public PlanIdentity.PatternIdentity identity(ICraftingPatternDetails value) {
                return new PlanIdentity.PatternIdentity(
                    PlanIdentity.Kind.DEFINITION,
                    value == first ? "first" : "second");
            }

            public ECOExecutionPlan.PatternRuntimeInfo runtimeInfo(ICraftingPatternDetails value) {
                return new ECOExecutionPlan.PatternRuntimeInfo(null, 1, Collections.emptyList());
            }

            public net.minecraft.nbt.NBTTagCompound write(Object key, long amount) {
                var tag = new net.minecraft.nbt.NBTTagCompound();
                tag.setString("key", (String) key);
                tag.setLong("amount", amount);
                return tag;
            }

            public cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack read(
                net.minecraft.nbt.NBTTagCompound tag) {
                return new cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack(
                    tag.getString("key"),
                    tag.getLong("amount"));
            }
        };
        var allocation = new cn.dancingsnow.neoecoae.crafting.planner.ported.result.PlannedInputAllocation(
            0,
            Collections.singletonList(
                new cn.dancingsnow.neoecoae.crafting.planner.ported.result.PlannedInputAllocation.Run(
                    "seed",
                    2,
                    4_000_000_000L)));
        var tasks = Arrays.asList(
            new ECOExecutionPlan.TaskSpec(
                0,
                codec.identity(first),
                first,
                codec.runtimeInfo(first),
                4_000_000_000L,
                0,
                ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC,
                Collections.singletonList(allocation)),
            new ECOExecutionPlan.TaskSpec(
                1,
                codec.identity(second),
                second,
                codec.runtimeInfo(second),
                2,
                1,
                ECOExecutionPlan.TaskKind.DAG));
        var phases = Arrays.asList(
            new ECOExecutionPlan.PhaseSpec(
                0,
                8,
                ECOExecutionSchedule.Type.DYNAMIC_CYCLE,
                Collections.singletonList(0),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonMap(0, 4_000_000_000L),
                Collections.singletonMap("seed", 2L)),
            new ECOExecutionPlan.PhaseSpec(
                1,
                9,
                ECOExecutionSchedule.Type.DAG,
                Collections.singletonList(1),
                Collections.emptyList(),
                Collections.singletonList(0)));
        var signature = new PlanIdentity.Signature(
            "goal",
            3,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap());
        var plan = new ECOExecutionPlan(
            signature,
            ExecutionMode.DYNAMIC_CYCLE,
            tasks,
            phases,
            new ECOExecutionSchedule(Collections.emptyList()));
        Map<ICraftingPatternDetails, Long> live = new LinkedHashMap<>();
        live.put(first, 4_000_000_000L);
        live.put(second, 2L);
        var productionBinding = new ECOExecutionBinding(plan, live.keySet(), live::get, true, codec);
        var production = new cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionRuntime(
            plan,
            productionBinding,
            codec);
        assertEquals(4_000_000_000L, production.allowance(first));
        live.put(first, 3_999_999_998L); // GTNH batch extras are committed before the first craft.
        production.accepted(first, 3, null);
        live.put(first, 3_999_999_997L); // Native AE2 then decrements the first craft.
        assertEquals(3_999_999_997L, production.allowance(first));
        var productionRestored = cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionRuntime
            .read(production.write(), live.keySet(), live::get, codec);
        assertEquals(3_999_999_997L, productionRestored.allowance(first));
        live.put(first, 4_000_000_000L);
        var binding = new ECOExecutionBinding(plan, live.keySet(), live::get, true, codec);
        var running = new ECOExecutionRuntime(plan, binding.patterns(), binding.progress());
        assertEquals(
            1,
            running.candidates()
                .size());
        var offered = running.candidates()
            .get(0);
        live.put(first, 3_999_999_999L);
        binding.refresh();
        running.onAccepted(offered, 1, null);
        assertEquals(
            3_999_999_999L,
            running.candidates()
                .get(0)
                .maxDispatchCount());
        assertThrows(
            IllegalArgumentException.class,
            () -> new ECOExecutionBinding(plan, live.keySet(), live::get, true, codec));
        var resumeBinding = new ECOExecutionBinding(plan, live.keySet(), live::get, false, codec);
        var resumed = ECOExecutionRuntime
            .read(plan, resumeBinding.patterns(), resumeBinding.progress(), running.write(codec), codec);
        assertEquals(
            3_999_999_999L,
            resumed.candidates()
                .get(0)
                .maxDispatchCount());
        var saved = ECOExecutionPlanCodec.writeExecutionPlan(plan, codec);
        var restored = ECOExecutionPlanCodec.readExecutionPlan(
            saved,
            codec,
            new cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack("goal", 3));
        assertEquals(plan.tasks(), restored.tasks());
        assertEquals(plan.phases(), restored.phases());
        assertEquals(
            1,
            restored.schedule()
                .dependencies()
                .size());
        saved.getTagList("phases", 10)
            .getCompoundTagAt(1)
            .setIntArray("dependencies", new int[] { 1 });
        assertThrows(
            IllegalArgumentException.class,
            () -> ECOExecutionPlanCodec.readExecutionPlan(
                saved,
                codec,
                new cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack("goal", 3)));
    }

    @Test
    void failedAttemptDoesNotAdvanceAndIndependentPhaseStaysReady() {
        ICraftingPatternDetails first = pattern();
        ICraftingPatternDetails second = pattern();
        var info = new ECOExecutionPlan.PatternRuntimeInfo(null, 0, Collections.emptyList());
        var firstTask = new ECOExecutionPlan.TaskSpec(
            0,
            new PlanIdentity.PatternIdentity(PlanIdentity.Kind.OBJECT, first),
            first,
            info,
            3,
            0,
            ECOExecutionPlan.TaskKind.CYCLE_ORDERED);
        var secondTask = new ECOExecutionPlan.TaskSpec(
            1,
            new PlanIdentity.PatternIdentity(PlanIdentity.Kind.OBJECT, second),
            second,
            info,
            2,
            1,
            ECOExecutionPlan.TaskKind.DAG);
        var ordered = new ECOExecutionPlan.PhaseSpec(
            0,
            0,
            ECOExecutionSchedule.Type.CYCLE,
            Collections.singletonList(0),
            Collections.singletonList(new ECOExecutionPlan.ExecutionStep(0, 3)),
            Collections.emptyList());
        var independent = new ECOExecutionPlan.PhaseSpec(
            1,
            1,
            ECOExecutionSchedule.Type.DAG,
            Collections.singletonList(1),
            Collections.emptyList(),
            Collections.emptyList());
        var signature = new PlanIdentity.Signature(
            "goal",
            1,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap());
        var plan = new ECOExecutionPlan(
            signature,
            ExecutionMode.ORDERED_CYCLE,
            Arrays.asList(firstTask, secondTask),
            Arrays.asList(ordered, independent),
            new ECOExecutionSchedule(Collections.emptyList()));
        Map<Integer, ICraftingPatternDetails> patterns = new LinkedHashMap<>();
        patterns.put(0, first);
        patterns.put(1, second);
        var progress = new ECOExecutionRuntime.TaskProgress[] { new ECOExecutionRuntime.TaskProgress(3),
            new ECOExecutionRuntime.TaskProgress(2) };
        var runtime = new ECOExecutionRuntime(plan, patterns, progress);
        assertEquals(
            2,
            runtime.candidates()
                .size());
        assertEquals(
            3,
            runtime.candidates()
                .get(0)
                .maxDispatchCount());
        assertEquals(
            3,
            runtime.candidates()
                .get(0)
                .maxDispatchCount());
        var accepted = runtime.candidates()
            .get(0);
        progress[0].value -= 1;
        runtime.onAccepted(accepted, 1, null);
        assertEquals(
            2,
            runtime.candidates()
                .get(0)
                .maxDispatchCount());
        assertEquals(
            2,
            runtime.candidates()
                .size());
        assertFalse(runtime.isComplete());
        ECOExecutionRuntime.ResourceCodec codec = new ECOExecutionRuntime.ResourceCodec() {

            public net.minecraft.nbt.NBTTagCompound write(Object key, long amount) {
                throw new AssertionError("No seed in this fixture");
            }

            public cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack read(
                net.minecraft.nbt.NBTTagCompound data) {
                throw new AssertionError("No seed in this fixture");
            }
        };
        var saved = runtime.write(codec);
        var restored = ECOExecutionRuntime.read(plan, patterns, progress, saved, codec);
        assertEquals(
            2,
            restored.candidates()
                .get(0)
                .maxDispatchCount());
        assertEquals(
            2,
            restored.candidates()
                .size());
        saved.setIntArray("stepCursor", new int[] { 5, 0 });
        assertThrows(
            IllegalArgumentException.class,
            () -> ECOExecutionRuntime.read(plan, patterns, progress, saved, codec));
    }
}
