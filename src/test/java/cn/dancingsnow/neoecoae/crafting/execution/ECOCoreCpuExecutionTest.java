package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItemRegistry;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;

class ECOCoreCpuExecutionTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void actualPlannerAndCpuDispatchTwoStageOrderAndDeliverItsRealOutput() throws Exception {
        var fixture = new Fixture();
        assertTrue(fixture.submit());
        assertNotNull(
            fixture.cpu.getLogic()
                .getJob().executionRuntime);
        var id = fixture.cpu.getLogic()
            .getLastLink()
            .getCraftingID();
        assertEquals(
            0,
            fixture.cpu.getLogic()
                .insertForJob(java.util.UUID.randomUUID(), fixture.goal, 7, Actionable.MODULATE));
        for (int tick = 0; tick < 10 && fixture.cpu.isBusy(); tick++) fixture.tickAndReturnOutputs();
        assertFalse(fixture.cpu.isBusy());
        assertFalse(fixture.cpu.hasRemainingItems());
        assertEquals(5L, fixture.stock.get(fixture.goal));
        assertEquals(0L, fixture.stock.get(fixture.raw));
        assertEquals(10, fixture.pushes);
        assertEquals(
            0,
            fixture.cpu.getLogic()
                .insertForJob(id, fixture.goal, 1, Actionable.MODULATE));
    }

    @Test
    void cancelReturnsOnlyCpuOwnedInputsAndKeepsTheLinkTerminal() throws Exception {
        var fixture = new Fixture();
        assertTrue(fixture.submit());
        var link = fixture.cpu.getLogic()
            .getLastLink();
        fixture.cpu.cancelJob();
        assertFalse(fixture.cpu.isBusy());
        assertTrue(link.isCanceled());
        assertEquals(5L, fixture.stock.get(fixture.raw));
        assertFalse(fixture.cpu.hasRemainingItems());
        fixture.cpu.cancelJob();
        assertEquals(5L, fixture.stock.get(fixture.raw));
        assertEquals(0, fixture.pushes);
    }

    @Test
    void activeWaitingAndStoredPagesReadTheirOwnLedgers() throws Exception {
        var fixture = new Fixture();
        assertTrue(fixture.submit());
        var runtime = new ECOCraftingNativeRuntime(fixture.cpu);
        var stored = new KeyCounter();
        var active = new KeyCounter();
        var pending = new KeyCounter();
        runtime.getItems(stored, CraftingItemList.STORAGE);
        runtime.getItems(active, CraftingItemList.ACTIVE);
        runtime.getItems(pending, CraftingItemList.PENDING);
        assertEquals(5, stored.get(fixture.raw));
        assertTrue(active.isEmpty());
        assertEquals(5, pending.get(fixture.intermediate));
        assertEquals(5, pending.get(fixture.goal));
        fixture.cpu.getLogic()
            .tickCraftingLogic(fixture.energy, fixture.service);
        stored = new KeyCounter();
        active = new KeyCounter();
        pending = new KeyCounter();
        runtime.getItems(stored, CraftingItemList.STORAGE);
        runtime.getItems(active, CraftingItemList.ACTIVE);
        runtime.getItems(pending, CraftingItemList.PENDING);
        assertTrue(stored.isEmpty());
        assertEquals(5, active.get(fixture.intermediate));
        assertEquals(0, pending.get(fixture.intermediate));
        assertEquals(5, pending.get(fixture.goal));
    }

    @Test
    void fullCpuSaveReloadContinuesWithoutReextractingInitialStock() throws Exception {
        var fixture = new Fixture();
        assertTrue(fixture.submit());
        fixture.cpu.getLogic()
            .tickCraftingLogic(fixture.energy, fixture.service);
        assertEquals(5, fixture.pending.size());
        CompoundTag data = new CompoundTag();
        fixture.cpu.writeToNBT(data, TestItemRegistry.CONTEXT);
        var previousId = fixture.cpu.getLogic()
            .getLastLink()
            .getCraftingID();
        var restored = new ECOCraftingCPU(fixture.host, null, fixture.owner);
        restored.bindNativeCPU(fixture.nativeCpu);
        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.decodePattern(any(), any()))
                .thenAnswer(call -> {
                    AEItemKey definition = call.getArgument(0);
                    return definition.equals(fixture.first.getDefinition()) ? fixture.first : fixture.second;
                });
            restored.readFromNBT(data, TestItemRegistry.CONTEXT);
        }
        fixture.cpu = restored;
        assertEquals(
            previousId,
            restored.getLogic()
                .getLastLink()
                .getCraftingID());
        assertFalse(
            restored.getLogic()
                .isJobSuspended());
        for (int tick = 0; tick < 10 && fixture.cpu.isBusy(); tick++) fixture.tickAndReturnOutputs();
        assertFalse(fixture.cpu.isBusy());
        assertEquals(5L, fixture.stock.get(fixture.goal));
        assertEquals(0L, fixture.stock.get(fixture.raw));
        assertEquals(10, fixture.pushes);
        verify(fixture.network, times(1)).extract(eq(fixture.raw), eq(5L), eq(Actionable.MODULATE), any());
    }

    @Test
    void aFailedAdmissionKeepsUnreturnableInputsForSavingAndRetry() throws Exception {
        var fixture = new Fixture();
        fixture.stock.put(fixture.raw, 3L);
        when(fixture.network.insert(eq(fixture.raw), anyLong(), eq(Actionable.MODULATE), any())).thenReturn(0L);
        assertFalse(fixture.submit());
        assertFalse(fixture.cpu.isBusy());
        assertEquals(
            3,
            fixture.cpu.getLogic()
                .getStored(fixture.raw));
        CompoundTag data = new CompoundTag();
        fixture.cpu.getLogic()
            .writeToNBT(data, TestItemRegistry.CONTEXT);
        assertFalse(
            data.getList("inventory", 10)
                .isEmpty());
        when(fixture.network.insert(eq(fixture.raw), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
            long amount = call.getArgument(1);
            fixture.stock.merge(fixture.raw, amount, Long::sum);
            return amount;
        });
        fixture.cpu.getLogic()
            .storeItems();
        assertFalse(fixture.cpu.hasRemainingItems());
        assertEquals(3L, fixture.stock.get(fixture.raw));
    }

    private static final class Fixture {

        final AEKey raw = AEItemKey.of(TestItems.RAW_IRON);
        final AEKey intermediate = AEItemKey.of(TestItems.IRON_INGOT);
        final AEKey goal = AEItemKey.of(TestItems.GOLD_INGOT);
        final IPatternDetails first = pattern(AEItemKey.of(TestItems.PAPER), raw, intermediate);
        final IPatternDetails second = pattern(AEItemKey.of(TestItems.BRICK), intermediate, goal);
        final Map<AEKey, Long> stock = new HashMap<>();
        final List<GenericStack> pending = new ArrayList<>();
        final GridView grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        final ICraftingService service = mock(ICraftingService.class);
        final IEnergyGrid energy = mock(IEnergyGrid.class);
        final MEStorage network = mock(MEStorage.class);
        final ECOCraftingCPUHost host = mock(ECOCraftingCPUHost.class);
        final ECOCraftingCPUOwner owner = mock(ECOCraftingCPUOwner.class);
        final appeng.api.networking.crafting.ICraftingCPU nativeCpu = mock(
            appeng.api.networking.crafting.ICraftingCPU.class);
        final ICraftingPlan plan;
        ECOCraftingCPU cpu;
        int pushes;

        Fixture() throws Exception {
            stock.put(raw, 5L);
            when(
                grid.getStorageService()
                    .getInventory()).thenReturn(network);
            when(grid.getCraftingService()).thenReturn(service);
            when(grid.getEnergyService()).thenReturn(energy);
            when(network.extract(any(), anyLong(), any(), any())).thenAnswer(call -> {
                AEKey key = call.getArgument(0);
                long offered = call.getArgument(1);
                long amount = Math.min(offered, stock.getOrDefault(key, 0L));
                if (call.getArgument(2) == Actionable.MODULATE) stock.merge(key, -amount, Long::sum);
                return amount;
            });
            when(network.insert(any(), anyLong(), any(), any())).thenAnswer(call -> {
                AEKey key = call.getArgument(0);
                long amount = call.getArgument(1);
                if (call.getArgument(2) == Actionable.MODULATE) stock.merge(key, amount, Long::sum);
                return amount;
            });
            when(energy.extractAEPower(anyDouble(), any(), any(appeng.api.config.PowerMultiplier.class)))
                .thenAnswer(call -> call.getArgument(0));
            when(host.getGrid()).thenReturn(grid);
            when(host.getLevel()).thenReturn(mock(World.class));
            when(host.getActionSource()).thenReturn(IActionSource.empty());
            when(host.getAvailableStorage()).thenReturn(1_000_000L);
            when(host.getPooledParallelism()).thenReturn(16);
            when(host.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
            when(host.isActive()).thenReturn(true);
            when(host.nativeCPU(any())).thenReturn(nativeCpu);
            when(owner.getTier()).thenReturn(StorageTiers.L4);
            when(service.getCraftingFor(raw)).thenReturn(Collections.emptyList());
            when(service.getCraftingFor(intermediate)).thenReturn(Collections.singletonList(first));
            when(service.getCraftingFor(goal)).thenReturn(Collections.singletonList(second));
            for (IPatternDetails pattern : new IPatternDetails[] { first, second }) {
                ICraftingProvider provider = mock(ICraftingProvider.class);
                when(provider.getAvailablePatterns()).thenReturn(Collections.singletonList(pattern));
                when(provider.pushPattern(eq(pattern), any())).thenAnswer(call -> {
                    pushes++;
                    pending.addAll(pattern.getOutputs());
                    return true;
                });
                when(service.getProviders(pattern)).thenReturn(Collections.singletonList(provider));
            }
            KeyCounter inventory = new KeyCounter();
            inventory.add(raw, 5);
            var result = new ECOCraftingPlannerService().createSession(service, goal, inventory)
                .plan(5, false, ECOCancellation.NONE);
            plan = result.plan();
            assertNotNull(plan);
            ECOPlanningResultRegistry.register(plan, result);
            cpu = new ECOCraftingCPU(host, plan, owner);
            cpu.bindNativeCPU(nativeCpu);
        }

        boolean submit() {
            return cpu.getLogic()
                .trySubmitJob(grid, plan, IActionSource.empty(), null)
                .successful();
        }

        void tickAndReturnOutputs() {
            if (!pending.isEmpty()) {
                List<GenericStack> returned = new ArrayList<>(pending);
                pending.clear();
                var id = cpu.getLogic()
                    .getLastLink()
                    .getCraftingID();
                for (var output : returned) assertEquals(
                    output.amount(),
                    cpu.getLogic()
                        .insertForJob(id, output.what(), output.amount(), Actionable.MODULATE));
            }
            cpu.getLogic()
                .tickCraftingLogic(energy, service);
        }
    }

    private static IPatternDetails pattern(AEItemKey definition, AEKey inputKey, AEKey outputKey) {
        IPatternDetails pattern = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(inputKey, 1) });
        when(input.getMultiplier()).thenReturn(1L);
        when(input.isValid(eq(inputKey), any())).thenReturn(true);
        when(pattern.getDefinition()).thenReturn(definition);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
        when(pattern.getOutputs()).thenReturn(Collections.singletonList(new GenericStack(outputKey, 1)));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(outputKey, 1));
        return pattern;
    }
}
