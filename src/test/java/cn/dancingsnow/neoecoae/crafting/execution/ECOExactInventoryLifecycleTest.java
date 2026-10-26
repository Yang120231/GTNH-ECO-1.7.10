package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.storage.MEStorage;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

class ECOExactInventoryLifecycleTest {

    @Test
    void internalExactFxReceiptAndCpuReturnKeepTheCompleteAmount() throws Exception {
        cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap.initialize();
        var cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
        var grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ECOBigIntegerStorage.class));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(
            grid.getStorageService()
                .getInventory()).thenReturn(storage);
        var logic = new ECOCraftingCPULogic(cpu);
        var input = cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey
            .of(cn.dancingsnow.neoecoae.platform.TestItems.IRON_INGOT);
        var output = cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey
            .of(cn.dancingsnow.neoecoae.platform.TestItems.DIAMOND);
        var amount = BigInteger.TEN.pow(28)
            .add(BigInteger.valueOf(17));
        var id = java.util.UUID.randomUUID();
        var link = mock(cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink.class);
        when(link.getCraftingID()).thenReturn(id);
        var plan = mock(cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack(output, 1));
        when(plan.emittedItems()).thenReturn(new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter());
        when(plan.patternTimes()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf());
        ExecutingCraftingJob job;
        try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
            job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
        }
        job.exactOrder = true;
        var waiting = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(ignored -> {});
        waiting.setEnabled(true);
        waiting.restore(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(input, amount));
        setField(job, "waitingFor", waiting);
        logic.setJobFromPersistence(job);
        java.util.function.Consumer<AEKey> listener = key -> assertEquals(
            BigInteger.ZERO,
            logic.insertExactForJob(id, input, amount, Actionable.MODULATE));
        logic.addListener(listener);
        assertEquals(
            BigInteger.ZERO,
            logic.insertExactForJob(java.util.UUID.randomUUID(), input, amount, Actionable.MODULATE));
        assertEquals(amount, logic.insertExactForJob(id, input, amount, Actionable.SIMULATE));
        assertEquals(amount, waiting.amount(input));
        assertEquals(amount, logic.insertExactForJob(id, input, amount, Actionable.MODULATE));
        logic.removeListener(listener);
        assertEquals(BigInteger.ZERO, waiting.amount(input));
        assertEquals(
            amount,
            logic.exactInventory()
                .amount(input));
        when(((ECOBigIntegerStorage) storage).insertBigInteger(eq(input), eq(amount), any(), any())).thenReturn(amount);
        logic.finishJob(false);
        assertFalse(logic.hasOwnedItems());
        verify((ECOBigIntegerStorage) storage).insertBigInteger(eq(input), eq(amount), eq(Actionable.MODULATE), any());
        verify(storage, never()).insert(any(), anyLong(), any(), any());
    }

    @Test
    void ecoStorageReceivesTheExactOverflowOnceAndSmallRemainderUsesLong() {
        var cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
        var grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ECOBigIntegerStorage.class));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(
            grid.getStorageService()
                .getInventory()).thenReturn(storage);
        var logic = new ECOCraftingCPULogic(cpu);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var total = BigInteger.TEN.pow(28)
            .add(BigInteger.valueOf(17));
        logic.exactInventory()
            .setEnabled(true);
        logic.exactInventory()
            .restore(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, total));
        var exact = (ECOBigIntegerStorage) storage;
        when(exact.insertBigInteger(eq(key), eq(total), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
            logic.storeItems(); // A storage callback must not replay the still-in-flight transfer.
            return total.subtract(BigInteger.valueOf(7));
        });
        logic.storeItems();
        assertEquals(
            BigInteger.valueOf(7),
            logic.exactInventory()
                .amount(key));
        verify(exact, times(1)).insertBigInteger(eq(key), eq(total), eq(Actionable.MODULATE), any());
        verify(storage, never()).insert(any(), anyLong(), any(), any());
        when(storage.insert(eq(key), eq(7L), eq(Actionable.MODULATE), any())).thenReturn(7L);
        logic.storeItems();
        assertEquals(
            BigInteger.ZERO,
            logic.exactInventory()
                .amount(key));
        verify(storage).insert(eq(key), eq(7L), eq(Actionable.MODULATE), any());
        assertFalse(logic.hasOwnedItems());
    }

    @Test
    void boundedReturnsPreferLongEvenWhenEcoStorageSupportsBigInteger() {
        var cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
        var grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ECOBigIntegerStorage.class));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(
            grid.getStorageService()
                .getInventory()).thenReturn(storage);
        var logic = new ECOCraftingCPULogic(cpu);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        logic.exactInventory()
            .setEnabled(true);
        logic.exactInventory()
            .restore(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, BigInteger.valueOf(64)));
        when(storage.insert(eq(key), eq(64L), eq(Actionable.MODULATE), any())).thenReturn(64L);
        logic.storeItems();
        assertFalse(logic.hasOwnedItems());
        verify(storage).insert(eq(key), eq(64L), eq(Actionable.MODULATE), any());
        verify((ECOBigIntegerStorage) storage, never()).insertBigInteger(any(), any(), any(), any());
    }

    @Test
    void failedExactReturnRetainsItsInventoryAndCanRetry() {
        var cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
        var grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        var storage = mock(MEStorage.class, withSettings().extraInterfaces(ECOBigIntegerStorage.class));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        when(
            grid.getStorageService()
                .getInventory()).thenReturn(storage);
        var logic = new ECOCraftingCPULogic(cpu);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var total = BigInteger.TEN.pow(28);
        logic.exactInventory()
            .setEnabled(true);
        logic.exactInventory()
            .restore(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(key, total));
        when(((ECOBigIntegerStorage) storage).insertBigInteger(eq(key), eq(total), any(), any()))
            .thenThrow(new IllegalStateException("rejected"))
            .thenReturn(total);
        assertThrows(IllegalStateException.class, logic::storeItems);
        assertEquals(
            total,
            logic.exactInventory()
                .amount(key));
        logic.storeItems();
        assertFalse(logic.hasOwnedItems());
    }

    @Test
    void onlyTheExplicitExactOrderFlagEnablesBigIntegerInventory() {
        var logic = new ECOCraftingCPULogic(mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS));
        var normal = mock(ExecutingCraftingJob.class);
        normal.exactOrder = false;
        logic.setJobFromLifecycle(normal);
        assertFalse(
            logic.exactInventory()
                .isEnabled());
        var exact = mock(ExecutingCraftingJob.class);
        exact.exactOrder = true;
        logic.setJobFromLifecycle(exact);
        assertTrue(
            logic.exactInventory()
                .isEnabled());
        logic.setJobFromLifecycle(null);
        assertFalse(
            logic.exactInventory()
                .isEnabled());
        logic.setJobFromPersistence(exact);
        assertTrue(
            logic.exactInventory()
                .isEnabled());
    }

    @Test
    void cancelledBigInventorySurvivesCpuSaveAndReturnsOneLongWindowPerPass() {
        var cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
        var grid = mock(GridView.class, RETURNS_DEEP_STUBS);
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getActionSource()).thenReturn(IActionSource.empty());
        var logic = new ECOCraftingCPULogic(cpu);
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var registry = mock(HolderLookup.Provider.class);
        when(key.toTagGeneric(registry)).thenAnswer(call -> new CompoundTag());
        logic.exactInventory()
            .setEnabled(true);
        for (int i = 0; i < 9; i++) logic.getInventory()
            .insert(key, Long.MAX_VALUE, Actionable.MODULATE);
        var saved = new CompoundTag();
        logic.writeToNBT(saved, registry);
        var restored = new ECOCraftingCPULogic(cpu);
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(eq(registry), any()))
                .thenReturn(key);
            restored.readFromNBT(saved, registry);
        }
        var total = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(BigInteger.valueOf(9));
        assertEquals(
            total,
            restored.exactInventory()
                .amount(key));
        var storage = grid.getStorageService()
            .getInventory();
        when(storage.insert(eq(key), anyLong(), eq(Actionable.MODULATE), any()))
            .thenAnswer(call -> call.getArgument(1));
        for (int i = 1; i <= 9; i++) {
            restored.storeItems();
            assertEquals(
                total.subtract(
                    BigInteger.valueOf(Long.MAX_VALUE)
                        .multiply(BigInteger.valueOf(i))),
                restored.exactInventory()
                    .amount(key));
        }
        verify(storage, times(9)).insert(eq(key), eq(Long.MAX_VALUE), eq(Actionable.MODULATE), any());
        assertFalse(restored.hasOwnedItems());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
