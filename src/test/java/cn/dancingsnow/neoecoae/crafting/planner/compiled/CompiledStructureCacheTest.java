package cn.dancingsnow.neoecoae.crafting.planner.compiled;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemantics;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOPatternSemanticsRegistry;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItemRegistry;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

class CompiledStructureCacheTest {

    private static AEKey raw, goal;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        raw = AEItemKey.of(TestItems.RAW_IRON);
        goal = AEItemKey.of(TestItems.IRON_INGOT);
    }

    @Test
    void separateServicesAndSessionsReuseStructureButCaptureFreshStockAndAmounts() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        assertEquals(
            PlanningStatus.SUCCESS,
            planner.createSession(service, goal, stock(5))
                .plan(5, false, ECOCancellation.NONE)
                .status());
        assertEquals(
            PlanningStatus.MISSING_ITEMS,
            new ECOCraftingPlannerService().createSession(service, goal, stock(0))
                .plan(1, false, ECOCancellation.NONE)
                .status());
        assertEquals(
            PlanningStatus.SUCCESS,
            planner.createSession(service, goal, stock(2))
                .plan(2, false, ECOCancellation.NONE)
                .status());
        verify(service, times(1)).getCraftingFor(goal);
        verify(service, times(1)).getCraftingFor(raw);
    }

    @Test
    void providerRefreshInvalidatesTheCompiledContract() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        assertEquals(
            PlanningStatus.SUCCESS,
            planner.createSession(service, goal, stock(5))
                .plan(1, false, ECOCancellation.NONE)
                .status());
        when(((ECOCraftingProviderRevision) service).neoecoae$getProviderRevision()).thenReturn(2L);
        when(service.getCraftingFor(goal)).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        assertEquals(
            PlanningStatus.MISSING_ITEMS,
            planner.createSession(service, goal, stock(5))
                .plan(1, false, ECOCancellation.NONE)
                .status());
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test
    void recipeReloadInvalidatesCrossSessionAndSessionLocalStructures() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        var session = planner.createSession(service, goal, stock(5));
        assertEquals(
            PlanningStatus.SUCCESS,
            session.plan(1, false, ECOCancellation.NONE)
                .status());
        when(service.getCraftingFor(goal)).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        PatternRuntime.onRecipeReloadOrServerReload();
        assertEquals(
            PlanningStatus.MISSING_ITEMS,
            session.plan(1, false, ECOCancellation.NONE)
                .status());
        assertEquals(
            PlanningStatus.MISSING_ITEMS,
            planner.createSession(service, goal, stock(5))
                .plan(1, false, ECOCancellation.NONE)
                .status());
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test
    void existingPlannerObservesRegisteredSpecialSemanticsAndRemoval() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        var session = planner.createSession(service, goal, stock(5));
        assertEquals(
            PlanningStatus.SUCCESS,
            session.plan(1, false, ECOCancellation.NONE)
                .status());
        IPatternDetails physical = service.getCraftingFor(goal)
            .iterator()
            .next();
        var id = ResourceLocation.fromNamespaceAndPath("eco_cache_test", "special");
        ECOPatternSemanticsRegistry.register(id, 1000, new ECOPatternSemanticAdapter() {

            @Override
            public boolean supports(IPatternDetails pattern) {
                return pattern == physical;
            }

            @Override
            public ECOPatternSemantics analyze(IPatternDetails pattern, ECOPatternSemantics base) {
                return ECOPatternSemantics.unsupported(pattern, base.physicalDefinition(), "SPECIAL_TEST_RESTRICTION");
            }
        });
        try {
            assertNotEquals(
                PlanningStatus.SUCCESS,
                session.plan(1, false, ECOCancellation.NONE)
                    .status());
            assertNotEquals(
                PlanningStatus.SUCCESS,
                planner.createSession(service, goal, stock(5))
                    .plan(1, false, ECOCancellation.NONE)
                    .status());
        } finally {
            ECOPatternSemanticsRegistry.unregister(id);
        }
        assertEquals(
            PlanningStatus.SUCCESS,
            session.plan(1, false, ECOCancellation.NONE)
                .status());
    }

    @Test
    void pendingProviderRebuildCannotReadOrPopulateTheCache() throws Exception {
        var service = service(true);
        var revision = (ECOCraftingProviderRevision) service;
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5))
            .plan(1, false, ECOCancellation.NONE);
        when(revision.neoecoae$isProviderSnapshotStable()).thenReturn(false);
        planner.createSession(service, goal, stock(5))
            .plan(1, false, ECOCancellation.NONE);
        when(revision.neoecoae$isProviderSnapshotStable()).thenReturn(true);
        planner.createSession(service, goal, stock(5))
            .plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5))
            .plan(1, false, ECOCancellation.NONE);
        verify(service, times(3)).getCraftingFor(goal);
    }

    @Test
    void cycleCapabilityAndComponentMatchingUseSeparateCacheEntries() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5), false)
            .plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5), true)
            .plan(1, false, ECOCancellation.NONE);
        planner
            .createSession(
                service,
                goal,
                stock(5),
                false,
                false,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(TestItemRegistry.ITEM.getKey(TestItems.RAW_IRON)))
            .plan(1, false, ECOCancellation.NONE);
        verify(service, times(3)).getCraftingFor(goal);
    }

    @Test
    void servicesWithoutStableRevisionEvidenceAlwaysCompileFresh() throws Exception {
        var service = service(false);
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5))
            .plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5))
            .plan(1, false, ECOCancellation.NONE);
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test
    void twoNetworksWithTheSameRevisionNeverSharePhysicalPatterns() throws Exception {
        var first = service(true);
        var second = service(true);
        when(second.getCraftingFor(goal)).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        var planner = new ECOCraftingPlannerService();
        assertEquals(
            PlanningStatus.SUCCESS,
            planner.createSession(first, goal, stock(5))
                .plan(1, false, ECOCancellation.NONE)
                .status());
        assertEquals(
            PlanningStatus.MISSING_ITEMS,
            planner.createSession(second, goal, stock(5))
                .plan(1, false, ECOCancellation.NONE)
                .status());
        verify(second).getCraftingFor(goal);
    }

    private static KeyCounter stock(long amount) {
        var stock = new KeyCounter();
        if (amount > 0) stock.add(raw, amount);
        return stock;
    }

    private static ICraftingService service(boolean revisioned) {
        var service = revisioned
            ? mock(ICraftingService.class, withSettings().extraInterfaces(ECOCraftingProviderRevision.class))
            : mock(ICraftingService.class);
        if (revisioned) {
            when(((ECOCraftingProviderRevision) service).neoecoae$getProviderRevision()).thenReturn(1L);
            when(((ECOCraftingProviderRevision) service).neoecoae$isProviderSnapshotStable()).thenReturn(true);
        }
        var input = mock(IPatternDetails.IInput.class);
        when(input.getMultiplier()).thenReturn(1L);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(raw, 1) });
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
        when(pattern.getOutputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(goal, 1)));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(goal, 1));
        when(service.getCraftingFor(goal)).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(pattern));
        when(service.getCraftingFor(raw)).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        return service;
    }
}
