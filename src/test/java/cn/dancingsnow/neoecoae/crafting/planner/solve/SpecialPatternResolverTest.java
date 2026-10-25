package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

class SpecialPatternResolverTest {

    @Test
    void returnedCatalystIsSharedAcrossPatternsButNotAcrossSimultaneousSlots() throws Exception {
        var key = org.mockito.Mockito.mock(cn.dancingsnow.neoecoae.platform.ae2.key.AEKey.class);
        var source = org.mockito.Mockito
            .mock(cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails.IInput.class);
        org.mockito.Mockito.when(source.getPossibleInputs())
            .thenReturn(
                new cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack[] {
                    new cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack(key, 1) });
        org.mockito.Mockito.when(source.getMultiplier())
            .thenReturn(1L);
        org.mockito.Mockito.when(source.getRemainingKey(key))
            .thenReturn(key);
        var input = new cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput(
            source,
            key,
            1L,
            true,
            "",
            key,
            1L);
        var requirement = new cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis.Requirement(
            input,
            key,
            cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis.Type.REUSABLE,
            0,
            0);
        var pattern = org.mockito.Mockito.mock(cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern.class);
        org.mockito.Mockito.when(pattern.inputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(input));
        org.mockito.Mockito.when(pattern.details())
            .thenReturn(org.mockito.Mockito.mock(cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails.class));
        org.mockito.Mockito.when(pattern.specialAnalysis())
            .thenReturn(
                new cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(requirement)));
        var stock = new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter();
        stock.add(key, 2);
        var state = new SolveState(stock);
        var resolver = new SpecialPatternResolver(
            null,
            state,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(),
            cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation.NONE,
            false);
        resolver.resolve(pattern, PlannerAmount.of(1000));
        resolver.resolve(pattern, PlannerAmount.of(2000));
        assertEquals(
            1L,
            state.usedItems()
                .get(key));
        org.mockito.Mockito.when(pattern.specialAnalysis())
            .thenReturn(
                new cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis(
                    cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(requirement, requirement)));
        resolver.resolve(pattern, PlannerAmount.of(3000));
        assertEquals(
            2L,
            state.usedItems()
                .get(key));
    }

    @Test
    void finalBreakingUseStillCountsAsOneCraft() {
        assertEquals(1, SpecialPatternResolver.durabilityUsesBeforeBreak(99, 4, 100));
    }

    @Test
    void durabilityCapacityRoundsUpToTheBreakingCraft() {
        assertEquals(3, SpecialPatternResolver.durabilityUsesBeforeBreak(90, 4, 100));
        assertEquals(0, SpecialPatternResolver.durabilityUsesBeforeBreak(100, 4, 100));
    }
}
