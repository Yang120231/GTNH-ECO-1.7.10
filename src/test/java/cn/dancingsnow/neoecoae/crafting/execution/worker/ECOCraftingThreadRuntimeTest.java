package cn.dancingsnow.neoecoae.crafting.execution.worker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyGrid;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItemRegistry;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.ae2.storage.StorageServiceView;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

class ECOCraftingThreadRuntimeTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void realAssemblerProgressAndPartialOwnerDeliverySurviveNbtWithoutNetworkLeak() {
        var fixture = new Fixture();
        assertTrue(fixture.start());
        assertNull(new ECOCraftingThread(fixture.worker).getOutputItem());
        fixture.ownerLimit = 1;
        for (int tick = 0; tick < 10; tick++) fixture.tick();
        assertEquals(1, fixture.delivered);
        assertEquals(2, fixture.thread.getDisplayedOutputAmount());
        assertTrue(fixture.thread.isBusy());
        verify(fixture.storage, never()).insert(any(), anyLong(), any(), any());
        verify(fixture.service, never()).insertIntoCpus(any(), anyLong(), any());

        var saved = fixture.thread.serializeNBT(TestItemRegistry.CONTEXT);
        fixture.thread = new ECOCraftingThread(fixture.worker);
        fixture.thread.deserializeNBT(TestItemRegistry.CONTEXT, saved);
        assertEquals(2, fixture.thread.getDisplayedOutputAmount());
        fixture.ownerLimit = Long.MAX_VALUE;
        fixture.tick();
        assertEquals(3, fixture.delivered);
        assertTrue(fixture.thread.isFree());
        assertEquals(0, fixture.thread.getDisplayedOutputAmount());
        verify(fixture.worker, times(1)).onBatchStarted();
        verify(fixture.worker, times(1)).onBatchStopped();
    }

    @Test
    void lostNetworkRetainsCompletedOutputUntilTheSameJobOwnerReturns() {
        var fixture = new Fixture();
        assertTrue(fixture.start());
        for (int tick = 0; tick < 9; tick++) fixture.tick();
        when(fixture.worker.getWorkerGrid()).thenReturn(null);
        fixture.tick();
        assertTrue(fixture.thread.isBusy());
        assertEquals(
            90,
            fixture.thread.createSnapshot()
                .progress());
        when(fixture.worker.getWorkerGrid()).thenReturn(fixture.grid);
        fixture.ownerLimit = 0;
        fixture.tick();
        assertTrue(
            fixture.thread.createSnapshot()
                .outputsReady());
        assertEquals(0, fixture.delivered);
        fixture.ownerLimit = Long.MAX_VALUE;
        fixture.tick();
        assertEquals(3, fixture.delivered);
        assertTrue(fixture.thread.isFree());
        verify(fixture.storage, never()).insert(any(), anyLong(), any(), any());
    }

    @Test
    void coolantAndPowerPauseProgressAndFractionalPowerSurvivesReload() {
        var fixture = new Fixture();
        assertTrue(fixture.start());
        when(fixture.controller.tryConsumeTickBasedCoolant(anyInt(), anyInt(), anyInt())).thenReturn(false);
        fixture.tick();
        assertEquals(
            0,
            fixture.thread.createSnapshot()
                .progress());
        verify(fixture.energy, never()).extractAEPower(anyDouble(), any(), any(PowerMultiplier.class));
        when(fixture.controller.tryConsumeTickBasedCoolant(anyInt(), anyInt(), anyInt())).thenReturn(true);
        fixture.powerLimit = .25;
        fixture.tick();
        fixture.tick();
        assertEquals(
            0,
            fixture.thread.createSnapshot()
                .progress());
        var saved = fixture.thread.serializeNBT(TestItemRegistry.CONTEXT);
        assertEquals(.5, saved.getDouble("progressRemainder"));
        fixture.thread = new ECOCraftingThread(fixture.worker);
        fixture.thread.deserializeNBT(TestItemRegistry.CONTEXT, saved);
        fixture.tick();
        fixture.tick();
        assertEquals(
            1,
            fixture.thread.createSnapshot()
                .progress());
        fixture.powerLimit = 0;
        fixture.tick();
        assertEquals(
            1,
            fixture.thread.createSnapshot()
                .progress());
        assertEquals(0, fixture.delivered);
    }

    @Test
    void recoveryBeforeCompletionReturnsConsumedInputsOnce() {
        var fixture = new Fixture();
        assertTrue(fixture.start());
        fixture.tick();
        assertTrue(fixture.thread.recoverInputsToNetwork(fixture.storage));
        assertTrue(fixture.thread.recoverInputsToNetwork(fixture.storage));
        assertEquals(1L, fixture.recovered.get(fixture.input));
        assertFalse(fixture.recovered.containsKey(fixture.output));
        assertEquals(0, fixture.delivered);
        assertTrue(fixture.thread.isFree());
    }

    @Test
    void invalidPersistedProgressQuarantinesCustodyAndReturnsInputs() {
        var fixture = new Fixture();
        assertTrue(fixture.start());
        var saved = fixture.thread.serializeNBT(TestItemRegistry.CONTEXT);
        saved.putInt("progress", -1);
        fixture.thread = new ECOCraftingThread(fixture.worker);
        fixture.thread.deserializeNBT(TestItemRegistry.CONTEXT, saved);
        assertTrue(fixture.thread.isBusy());
        fixture.thread.tickRecovery();
        assertTrue(fixture.thread.isFree());
        assertEquals(1L, fixture.recovered.get(fixture.input));
        assertFalse(fixture.recovered.containsKey(fixture.output));
        assertEquals(0, fixture.delivered);
    }

    private static final class Fixture {

        final AEItemKey input = AEItemKey.of(TestItems.RAW_IRON);
        final AEItemKey output = AEItemKey.of(TestItems.IRON_INGOT);
        final UUID job = UUID.randomUUID();
        final ECOCraftingWorkerRuntimeHost worker = mock(ECOCraftingWorkerRuntimeHost.class);
        final ECOCraftingControllerHost controller = mock(ECOCraftingControllerHost.class);
        final GridView grid = mock(GridView.class);
        final World world = mock(World.class);
        final IEnergyGrid energy = mock(IEnergyGrid.class);
        final StorageServiceView storageService = mock(StorageServiceView.class);
        final MEStorage storage = mock(MEStorage.class);
        final ICraftingService service = mock(
            ICraftingService.class,
            withSettings().extraInterfaces(ECOCraftingOutputRouter.class));
        final ECOCraftingOutputRouter router = (ECOCraftingOutputRouter) service;
        final Map<AEKey, Long> recovered = new HashMap<>();
        final AtomicLong gameTick = new AtomicLong();
        final IMolecularAssemblerSupportedPattern pattern = mock(IMolecularAssemblerSupportedPattern.class);
        ECOCraftingThread thread;
        double powerLimit = Double.MAX_VALUE;
        long ownerLimit = Long.MAX_VALUE;
        long delivered;

        Fixture() {
            when(worker.getNativeLevel()).thenReturn(world);
            when(worker.getWorkerGrid()).thenReturn(grid);
            when(worker.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(worker.getFastPathCache()).thenReturn(new ECOCraftingFastPathCache(16));
            when(world.getTotalWorldTime()).thenAnswer(call -> gameTick.get());
            when(grid.getEnergyService()).thenReturn(energy);
            when(grid.getCraftingService()).thenReturn(service);
            when(grid.getStorageService()).thenReturn(storageService);
            when(storageService.getInventory()).thenReturn(storage);
            when(controller.tryConsumeTickBasedCoolant(anyInt(), anyInt(), anyInt())).thenReturn(true);
            when(energy.extractAEPower(anyDouble(), eq(Actionable.MODULATE), any(PowerMultiplier.class)))
                .thenAnswer(call -> Math.min(powerLimit, (double) call.getArgument(0)));
            when(router.neoecoae$insertIntoCpuForJob(eq(job), eq(output), anyLong(), eq(Actionable.MODULATE)))
                .thenAnswer(call -> {
                    long accepted = Math.min(ownerLimit, (long) call.getArgument(2));
                    ownerLimit -= accepted;
                    delivered += accepted;
                    return accepted;
                });
            when(storage.insert(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
                AEKey key = call.getArgument(0);
                long amount = call.getArgument(1);
                recovered.merge(key, amount, Long::sum);
                return amount;
            });
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
            when(pattern.getOutputs()).thenReturn(Collections.singletonList(new GenericStack(output, 3)));
            doAnswer(call -> {
                IMolecularAssemblerSupportedPattern.CraftingGridAccessor access = call.getArgument(1);
                access.set(0, input.toStack(1));
                return null;
            }).when(pattern)
                .fillCraftingGrid(any(), any());
            when(pattern.assemble(any(), eq(world))).thenAnswer(call -> {
                cn.dancingsnow.neoecoae.platform.minecraft.crafting.CraftingInput inputGrid = call.getArgument(0);
                assertEquals(1, inputGrid.size());
                assertSame(
                    TestItems.RAW_IRON,
                    inputGrid.getItem(0)
                        .getItem());
                return new ItemStack(TestItems.IRON_INGOT, 3);
            });
            when(pattern.getRemainingItems(any())).thenAnswer(call -> Collections.singletonList(null));
            thread = new ECOCraftingThread(worker);
        }

        boolean start() {
            var inputs = new KeyCounter();
            inputs.add(input, 1);
            return thread
                .pushPattern(ECOExtractedPatternExecution.slow(pattern, new KeyCounter[] { inputs }), controller, job);
        }

        void tick() {
            gameTick.incrementAndGet();
            thread.tick(controller, 0, 1, 1);
        }
    }
}
