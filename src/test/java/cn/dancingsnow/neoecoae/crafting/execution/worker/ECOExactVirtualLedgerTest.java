package cn.dancingsnow.neoecoae.crafting.execution.worker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

class ECOExactVirtualLedgerTest {

    @BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap.initialize();
    }

    @Test
    void exactCustodySurvivesPartialDeliveryAndReload() {
        var key = mock(AEKey.class);
        var registries = mock(HolderLookup.Provider.class);
        when(key.toTagGeneric(registries)).thenAnswer(call -> new CompoundTag());
        var count = BigInteger.TEN.pow(28)
            .add(BigInteger.valueOf(17));
        var ledger = new ECOExactVirtualLedger(
            count,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 9)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 2)),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(key, 1)));
        assertEquals(
            count.multiply(BigInteger.valueOf(9)),
            ledger.snapshot(false)
                .get(key));
        assertFalse(ledger.drain(true, (k, offered) -> {
            assertEquals(Long.MAX_VALUE, offered.longValue());
            return 17L;
        }, () -> {}));
        var remaining = count.multiply(BigInteger.valueOf(3))
            .subtract(BigInteger.valueOf(17));
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(eq(registries), any()))
                .thenReturn(key);
            var restored = ECOExactVirtualLedger.read(ledger.write(registries), registries);
            assertEquals(count, restored.crafts());
            assertEquals(
                remaining,
                restored.snapshot(true)
                    .get(key));
            assertThrows(
                IllegalStateException.class,
                () -> restored.drain(true, (k, n) -> { throw new IllegalStateException(); }, () -> {}));
            assertEquals(
                remaining,
                restored.snapshot(true)
                    .get(key));
            assertFalse(restored.drain(false, (k, n) -> n, () -> {}));
            assertEquals(
                count.multiply(BigInteger.valueOf(9))
                    .subtract(BigInteger.valueOf(Long.MAX_VALUE)),
                restored.snapshot(false)
                    .get(key));
        }
    }

    @Test
    void exactLaneUsesOneSlotAndRejectsBusyOrNonVirtualWorker() {
        var worker = mock(ECOCraftingWorkerRuntimeHost.class);
        var controller = mock(ECOCraftingControllerHost.class);
        var cache = mock(ECOCraftingFastPathCache.class);
        var recipe = mock(ECOVerifiedFastPathRecipe.class);
        var key = AEItemKey.of(TestItems.IRON_INGOT);
        var inputs = Collections.singletonList(new GenericStack(key, 9));
        var outputs = Collections.singletonList(new GenericStack(key, 1));
        when(worker.getFastPathCache()).thenReturn(cache);
        when(worker.isControlledBy(controller)).thenReturn(true);
        when(recipe.isIssuedBy(cache)).thenReturn(true);
        when(recipe.isCurrent(anyLong())).thenReturn(true);
        when(recipe.inputsPerCraft()).thenReturn(inputs);
        when(recipe.outputsPerCraft()).thenReturn(outputs);
        when(recipe.remainingPerCraft()).thenReturn(Collections.emptyList());
        var job = UUID.randomUUID();
        when(recipe.withVirtualBatch(1L, job))
            .thenReturn(new ECOVerifiedVirtualExecution(recipe, 1L, job, inputs, outputs, Collections.emptyList()));
        var thread = new ECOCraftingThread(worker);
        var count = BigInteger.TEN.pow(28)
            .add(BigInteger.ONE);
        assertFalse(thread.pushExactVirtualBatch(recipe, count, job, controller));
        when(controller.isFullVirtualCraftingMode()).thenReturn(true);
        assertTrue(thread.pushExactVirtualBatch(recipe, count, job, controller));
        assertEquals(count, thread.getExactCraftCount());
        assertEquals(1, thread.getFiniteBatchCraftCount());
        assertFalse(thread.pushExactVirtualBatch(recipe, count, job, controller));
        verify(worker, times(1)).onBatchStarted();
    }
}
