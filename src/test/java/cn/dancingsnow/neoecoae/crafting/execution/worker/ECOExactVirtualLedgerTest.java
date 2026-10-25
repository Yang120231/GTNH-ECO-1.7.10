package cn.dancingsnow.neoecoae.crafting.execution.worker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
