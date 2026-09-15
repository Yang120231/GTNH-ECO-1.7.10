package cn.dancingsnow.neoecoae.crafting.runtime.ported;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyGrid;

class ECOEnergyTransactionTest {

    private static final class Grid {

        double charged;
        double refunded;
        double available = 1000;
        boolean rejectRefund;
        IEnergyGrid api = (IEnergyGrid) Proxy.newProxyInstance(
            IEnergyGrid.class.getClassLoader(),
            new Class<?>[] { IEnergyGrid.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("extractAEPower")) {
                    double value = Math.min((double) args[0], available);
                    charged += value;
                    return value;
                }
                if (method.getName()
                    .equals("injectPower")) {
                    if (rejectRefund) throw new IllegalStateException("refund unavailable");
                    refunded += (double) args[0];
                    return 500D; // GTNH overflow is already owned by the grid.
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    @Test
    void rejectionRefundUsesRawUnitsAndDoesNotDuplicateStoredOverflow() {
        double multiplier = PowerMultiplier.CONFIG.multiplier;
        try {
            PowerMultiplier.CONFIG.multiplier = 3;
            Grid grid = new Grid();
            ECOCraftingEnergyTransaction ledger = new ECOCraftingEnergyTransaction(() -> {}, () -> 0);
            var reservation = ledger.reserve(grid.api, 10);
            assertNotNull(reservation);
            reservation.refund();
            reservation.refund();
            assertEquals(30D, grid.refunded);
            ledger.returnIdleCredit(grid.api);
            assertEquals(30D, grid.refunded);
        } finally {
            PowerMultiplier.CONFIG.multiplier = multiplier;
        }
    }

    @Test
    void partialChargeRejectsAndRefundsBeforeDispatch() {
        Grid grid = new Grid();
        grid.available = 4;
        var ledger = new ECOCraftingEnergyTransaction(() -> {}, () -> 0);
        assertNull(ledger.reserve(grid.api, 10));
        assertEquals(PowerMultiplier.CONFIG.multiply(4), grid.refunded);
    }

    @Test
    void failedRefundRemainsCreditAndAcceptedReservationConsumesItOnce() {
        Grid grid = new Grid();
        var ledger = new ECOCraftingEnergyTransaction(() -> {}, () -> 0);
        var reservation = ledger.reserve(grid.api, 10);
        grid.rejectRefund = true;
        reservation.refund();
        var retry = ledger.reserve(grid.api, 10);
        assertEquals(10D, grid.charged);
        retry.commit();
        retry.refund();
        assertEquals(0D, grid.refunded);
    }
}
