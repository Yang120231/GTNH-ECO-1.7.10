package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class ByproductReviewProbeTest {

    private static AEKey ore, ingot, slag, plate, goal, missing;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        ore = AEItemKey.of(TestItems.RAW_IRON);
        ingot = AEItemKey.of(TestItems.IRON_INGOT);
        slag = AEItemKey.of(TestItems.GRAVEL);
        plate = AEItemKey.of(TestItems.IRON_NUGGET);
        goal = AEItemKey.of(TestItems.BRICK);
        missing = AEItemKey.of(TestItems.DIAMOND);
    }

    static Stream<Arguments> cases() {
        return Stream.of(false, true)
            .flatMap(
                cycles -> Stream.of(false, true)
                    .flatMap(
                        delayed -> Stream.of(false, true)
                            .map(first -> Arguments.of(cycles, delayed, first))));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void primaryAndByproductShareOnePhysicalFiring(boolean cycles, boolean delayed, boolean byproductFirst)
        throws Exception {
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore)),
            stack(ingot),
            stack(slag));
        var convert = pattern(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ingot)), stack(plate));
        AEKey primary = delayed ? plate : ingot;
        var finish = pattern(
            byproductFirst ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(slag), stack(primary))
                : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(primary), stack(slag)),
            stack(goal));
        var stock = stock(ore, 5);
        var result = plan(service(upstream, convert, finish), stock, goal, 5, cycles);
        assertEquals(
            PlanningStatus.SUCCESS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        assertEquals(
            5L,
            result.plan()
                .patternTimes()
                .get(upstream));
        assertEquals(
            PlannerAmount.of(5),
            result.exactUsedItems()
                .get(ore));
        assertEquals(5L, stock.get(ore), "Planning must not mutate real inventory");
        var provenance = result.provenance();
        provenance.requireComplete();
        assertTrue(
            provenance.allocationsFor(finish)
                .stream()
                .anyMatch(
                    a -> a.material()
                        .equals(slag)
                        && a.source()
                            .equals(new MaterialSource.PatternOutput(upstream, false))));
        replay(result, stock, goal, 5);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void jointOutputAvoidsAnUnnecessaryRecipeWithMissingInputs(boolean cycles) throws Exception {
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore)),
            stack(ingot),
            stack(slag));
        var convert = pattern(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ingot)), stack(plate));
        var unnecessary = pattern(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(missing)), stack(slag));
        var finish = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(slag), stack(plate)),
            stack(goal));
        var result = plan(service(upstream, convert, unnecessary, finish), stock(ore, 1), goal, 1, cycles);
        assertEquals(
            PlanningStatus.SUCCESS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        assertFalse(
            result.plan()
                .patternTimes()
                .containsKey(unnecessary));
        assertFalse(
            result.exactUsedItems()
                .containsKey(missing));
        assertTrue(
            result.exactMissingItems()
                .isEmpty());
        replay(result, stock(ore, 1), goal, 1);
    }

    @Test
    void byproductShortfallNeverAddsFiringsToItsSource() throws Exception {
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore)),
            stack(ingot),
            stack(slag));
        var finish = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(slag, 3), stack(ingot)),
            stack(goal));
        var result = plan(service(upstream, finish), stock(ore, 10), goal, 1, true);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertEquals(
            1L,
            result.plan()
                .patternTimes()
                .get(upstream));
        assertEquals(
            PlannerAmount.of(2),
            result.exactMissingItems()
                .get(slag));
        assertEquals(
            PlannerAmount.ONE,
            result.exactUsedItems()
                .get(ore));
    }

    @Test
    void requestingOnlyAByproductNeverStartsThePrimaryRecipe() throws Exception {
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore)),
            stack(ingot),
            stack(slag));
        var result = plan(service(upstream), stock(ore, 10), slag, 3, true);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertTrue(
            result.exactPatternTimes()
                .isEmpty());
        assertEquals(
            PlannerAmount.of(3),
            result.exactMissingItems()
                .get(slag));
    }

    @Test
    void equivalentJointPatternsPublishAClosedLargePlanWithinTheSharedBudget() throws Exception {
        var hydrogenFirst = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(ore, 3000)),
            new GenericStack(ingot, 2000),
            new GenericStack(slag, 1000));
        var oxygenFirst = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(ore, 3000)),
            new GenericStack(slag, 1000),
            new GenericStack(ingot, 2000));
        var finish = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new GenericStack(ingot, 27_615_000), new GenericStack(slag, 1_503_000)),
            stack(goal));
        var inventory = stock(ore, 32_049_000);
        inventory.add(ingot, 6_249_500);
        inventory.add(slag, 16_934_750);
        var session = new ECOCraftingPlannerService()
            .createSession(service(hydrogenFirst, oxygenFirst, finish), goal, inventory, true);
        var field = session.getClass()
            .getDeclaredField("planningBudget");
        field.setAccessible(true);
        field.set(session, new ECOPlanningBudget(ECOCancellation.NONE, 5000, Long.MAX_VALUE, () -> 0L));
        var result = session.plan(1, false, ECOCancellation.NONE);
        assertEquals(
            PlanningStatus.SUCCESS,
            result.status(),
            result.trace()
                .diagnostics()
                .toString());
        long runs = result.plan()
            .patternTimes()
            .getOrDefault(hydrogenFirst, 0L)
            + result.plan()
                .patternTimes()
                .getOrDefault(oxygenFirst, 0L);
        assertEquals(10_683L, runs);
        assertEquals(
            PlannerAmount.of(32_049_000),
            result.exactUsedItems()
                .get(ore));
        assertEquals(
            1L,
            result.plan()
                .patternTimes()
                .get(finish));
        assertTrue(
            result.exactMissingItems()
                .isEmpty());
        assertNotNull(result.executionPlan());
        result.provenance()
            .requireComplete();
        var schedule = ECOExecutionSchedule.from(
            result.components(),
            result.executionComponentOrder(),
            result.plan()
                .patternTimes(),
            result.provenance());
        int supplier = -1, consumer = -1;
        for (int i = 0; i < schedule.phases()
            .size(); i++) {
            var tasks = schedule.phases()
                .get(i)
                .patternSet();
            if (tasks.contains(hydrogenFirst) || tasks.contains(oxygenFirst)) supplier = i;
            if (tasks.contains(finish)) consumer = i;
        }
        assertTrue(supplier >= 0 && consumer > supplier, "Joint supply must execute before its consumer");
        assertEquals(32_049_000L, inventory.get(ore), "Planning must preserve the network snapshot");
    }

    @Test
    void leftoverJointDemandUsesItsOwnPrimaryRecipeWithoutRepeatingTheSource() throws Exception {
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore)),
            stack(ingot),
            stack(slag));
        var supplemental = pattern(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(missing)), stack(slag));
        var finish = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(slag, 3), stack(ingot)),
            stack(goal));
        var inventory = stock(ore, 1);
        inventory.add(missing, 2);
        var result = plan(service(upstream, supplemental, finish), inventory, goal, 1, true);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(
            1L,
            result.plan()
                .patternTimes()
                .get(upstream));
        assertEquals(
            2L,
            result.plan()
                .patternTimes()
                .get(supplemental));
        replay(result, inventory, goal, 1);
    }

    @Test
    void retryDiscardsFailedRoutesJointCreditAndReservations() throws Exception {
        var rejected = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(missing)),
            stack(ingot),
            new GenericStack(slag, 2));
        var accepted = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore)),
            stack(ingot),
            stack(slag));
        var finish = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(slag, 2), stack(ingot)),
            stack(goal));
        var result = plan(service(rejected, accepted, finish), stock(ore, 1), goal, 1, true);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertFalse(
            result.plan()
                .patternTimes()
                .containsKey(rejected));
        assertFalse(
            result.exactUsedItems()
                .containsKey(missing));
        assertEquals(
            1L,
            result.plan()
                .patternTimes()
                .get(accepted));
        assertEquals(
            PlannerAmount.ONE,
            result.exactMissingItems()
                .get(slag));
        assertTrue(
            result.provenance()
                .supplierAmountsOf(slag)
                .keySet()
                .stream()
                .noneMatch(
                    source -> source instanceof MaterialSource.PatternOutput output && output.pattern() == rejected));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void ancestorCannotBorrowItsDescendantsByproductWithoutSeed(boolean cycles) throws Exception {
        var upstream = pattern(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ore)),
            stack(ingot),
            stack(slag));
        var convert = pattern(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(slag)), stack(ore));
        var finish = pattern(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(stack(ingot)), stack(goal));
        var svc = service(upstream, convert, finish);
        var unseeded = plan(svc, new KeyCounter(), goal, 3, cycles);
        assertNotEquals(PlanningStatus.SUCCESS, unseeded.status());
        if (cycles) {
            var seeded = plan(svc, stock(slag, 1), goal, 3, true);
            assertEquals(
                PlanningStatus.SUCCESS,
                seeded.status(),
                seeded.trace()
                    .diagnostics()
                    .toString());
            assertFalse(
                seeded.cycles()
                    .isEmpty());
            assertNotNull(seeded.executionPlan());
        }
    }

    private static void replay(ECOPlanningResult result, KeyCounter inventory, AEKey output, long amount) {
        var actual = new LinkedHashMap<AEKey, Long>();
        result.exactUsedItems()
            .forEach((key, count) -> {
                assertTrue(inventory.get(key) >= count.longValueExact());
                actual.put(key, count.longValueExact());
            });
        var schedule = ECOExecutionSchedule.from(
            result.components(),
            result.executionComponentOrder(),
            result.plan()
                .patternTimes(),
            result.provenance());
        for (var phase : schedule.phases()) {
            assertEquals(ECOExecutionSchedule.Type.DAG, phase.type());
            for (var pattern : phase.patternSet()) {
                long times = result.plan()
                    .patternTimes()
                    .get(pattern);
                for (var input : pattern.getInputs()) {
                    var stack = input.getPossibleInputs()[0];
                    long needed = stack.amount() * input.getMultiplier() * times;
                    assertTrue(
                        actual.getOrDefault(stack.what(), 0L) >= needed,
                        "Schedule used output before its supplier: " + stack.what());
                    actual.merge(stack.what(), -needed, Long::sum);
                }
                for (var stack : pattern.getOutputs()) actual.merge(stack.what(), stack.amount() * times, Long::sum);
            }
        }
        assertTrue(actual.getOrDefault(output, 0L) >= amount);
    }

    private static ECOPlanningResult plan(ICraftingService svc, KeyCounter stock, AEKey key, long amount,
        boolean cycles) throws Exception {
        return new ECOCraftingPlannerService().createSession(svc, key, stock, cycles)
            .plan(amount, false, ECOCancellation.NONE);
    }

    private static KeyCounter stock(AEKey key, long amount) {
        var stock = new KeyCounter();
        stock.add(key, amount);
        return stock;
    }

    private static GenericStack stack(AEKey key) {
        return new GenericStack(key, 1);
    }

    private static IPatternDetails pattern(List<GenericStack> inputs, GenericStack... outputs) {
        var details = mock(IPatternDetails.class);
        var raw = inputs.stream()
            .map(stack -> {
                var input = mock(IPatternDetails.IInput.class);
                when(input.getMultiplier()).thenReturn(1L);
                when(input.getPossibleInputs()).thenReturn(new GenericStack[] { stack });
                when(input.isValid(any(), any())).thenAnswer(
                    call -> stack.what()
                        .equals(call.getArgument(0)));
                return input;
            })
            .toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(raw);
        when(details.getOutputs()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(outputs));
        when(details.getPrimaryOutput()).thenReturn(outputs[0]);
        return details;
    }

    private static ICraftingService service(IPatternDetails... patterns) {
        Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
        for (var pattern : patterns) {
            index.computeIfAbsent(
                pattern.getPrimaryOutput()
                    .what(),
                ignored -> new ArrayList<>())
                .add(pattern);
        }
        var svc = mock(ICraftingService.class);
        when(svc.getCraftingFor(any())).thenAnswer(
            call -> index.getOrDefault(call.getArgument(0), cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()));
        return svc;
    }
}
