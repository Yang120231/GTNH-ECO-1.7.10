package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

class StoredInputSnapshotTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void fuzzyConsumptionKeepsAllRemovedVariantKeys(boolean special) throws Exception {
        var template = AEItemKey.of(TestItems.IRON_PICKAXE);
        var goal = AEItemKey.of(TestItems.DIAMOND);
        int variants = 64;
        var input = new CompiledInput(
            null,
            template,
            PlannerAmount.of(variants),
            true,
            null,
            null,
            PlannerAmount.ZERO,
            true);
        var details = mock(IPatternDetails.class);
        var outputs = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(goal, 1L));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(
            details,
            null,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            outputs,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            PatternSemantics.MatchingMode.EXACT,
            PatternSemantics.ExecutionRestriction.NONE,
            true,
            true,
            null);
        var analysis = special ? new SpecialPatternAnalysis(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(
                new SpecialPatternAnalysis.Requirement(input, template, SpecialPatternAnalysis.Type.CATALYST, 0, 0)))
            : SpecialPatternAnalysis.NONE;
        var pattern = new CompiledPattern(
            0,
            details,
            goal,
            PlannerAmount.ONE,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(input),
            outputs,
            true,
            null,
            false,
            semantics,
            analysis);
        var stock = new KeyCounter();
        for (int i = 1; i <= variants; i++) {
            var stack = new ItemStack(TestItems.IRON_PICKAXE);
            stack.setItemDamage(i);
            stock.add(AEItemKey.of(stack), 1L);
        }
        SolveState state;
        if (special) {
            state = new SolveState(stock);
            new SpecialPatternResolver(
                null,
                state,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
                ECOCancellation.NONE,
                false).resolve(pattern, PlannerAmount.ONE);
        } else {
            var network = new CompiledNetwork(
                goal,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(
                    goal,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pattern),
                    template,
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(),
                1,
                1);
            var result = new AcyclicCraftingSolver().solve(
                network,
                new AcyclicRoutePlan(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(goal, template)),
                stock,
                1L,
                ECOCancellation.NONE);
            assertEquals(PlanningStatus.SUCCESS, result.status());
            state = result.state();
        }
        assertTrue(state.stored.isEmpty());
        assertEquals(
            variants,
            state.used.asMap()
                .size());
        for (var entry : stock) assertEquals(
            1L,
            state.usedItems()
                .get(entry.getKey()));
        state.executionProvenance()
            .requireComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 64 })
    void exhaustingDurabilityVariantsKeepsTheirMaterialIdentity(int variants) throws Exception {
        var template = new ItemStack(TestItems.IRON_PICKAXE);
        var key = AEItemKey.of(template);
        var input = new CompiledInput(null, key, 1L, true, null);
        var requirement = new SpecialPatternAnalysis.Requirement(
            input,
            key,
            SpecialPatternAnalysis.Type.DURABILITY,
            1,
            template.getMaxDamage());
        var pattern = mock(CompiledPattern.class);
        when(pattern.inputs()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(input));
        when(pattern.details()).thenReturn(mock(IPatternDetails.class));
        when(pattern.specialAnalysis())
            .thenReturn(new SpecialPatternAnalysis(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(requirement)));
        var state = new SolveState(new KeyCounter());
        List<AEKey> storedKeys = new ArrayList<>();
        long uses = 0;
        for (int i = 1; i <= variants; i++) {
            var stack = template.copy();
            stack.setItemDamage(template.getMaxDamage() - i);
            var storedKey = AEItemKey.of(stack);
            storedKeys.add(storedKey);
            state.stored.add(storedKey, 1L);
            uses += i;
        }
        new SpecialPatternResolver(
            null,
            state,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            ECOCancellation.NONE,
            false).resolve(pattern, PlannerAmount.of(uses));
        assertTrue(state.stored.isEmpty());
        assertEquals(
            variants,
            state.used.asMap()
                .size());
        for (AEKey storedKey : storedKeys) assertEquals(
            1L,
            state.usedItems()
                .get(storedKey));
        state.executionProvenance()
            .requireComplete();
        assertEquals(
            variants,
            state.executionProvenance()
                .allocations()
                .size());
    }
}
