package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningImprovements;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.TestAddonKey;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;

class ECOCraftConfirmSessionTest {

    private static ECOCraftConfirmReports.Built built(long version, PlanningStatus status, boolean simulation) {
        var state = new ECOCraftConfirmState(
            true,
            true,
            false,
            false,
            42L,
            BigInteger.valueOf(64),
            status,
            "",
            0,
            simulation,
            64L,
            version,
            null,
            "");
        var report = new ECOCraftConfirmReport(
            version,
            Collections.singletonList(new ECOCraftConfirmReport.Row(TestAddonKey.INSTANCE, 3, 1, 2)),
            ECOCycleItemList.EMPTY);
        return new ECOCraftConfirmReports.Built(state, report);
    }

    @Test
    void nativeConfirmationsStaySilent() {
        var session = new ECOCraftConfirmSession();
        session.observeResult(new Object(), version -> fail("A native result must never be described"));
        assertNull(session.drain());
        assertSame(ECOCraftConfirmState.NONE, session.state());
    }

    @Test
    void calculatingStateIsSentBeforeTheReportAndTheReportOnlyOnce() {
        var session = new ECOCraftConfirmSession();
        session.begin(true);
        var calculating = session.drain();
        assertNotNull(calculating);
        assertTrue(calculating.state.plannerAvailable);
        assertFalse(calculating.state.reportReady);
        assertNull(calculating.report, "No report exists while ECO is still planning");
        assertNull(session.drain(), "An unchanged header is not resent");

        Object result = new Object();
        AtomicInteger builds = new AtomicInteger();
        session.observeResult(result, version -> {
            builds.incrementAndGet();
            return built(version, PlanningStatus.SUCCESS, false);
        });
        session.observeResult(result, version -> {
            builds.incrementAndGet();
            return built(version, PlanningStatus.SUCCESS, false);
        });
        assertEquals(1, builds.get(), "The same result is described once");
        var ready = session.drain();
        assertNotNull(ready);
        assertTrue(ready.state.reportReady);
        assertNotNull(ready.report);
        assertEquals(ready.state.reportVersion, ready.report.version);

        session.setCpuTier(9);
        var tier = session.drain();
        assertNotNull(tier);
        assertEquals(9, tier.state.selectedCpuTier);
        assertNull(tier.report, "A CPU change resends only the header");
    }

    @Test
    void aNewPlanReplacesTheReportAndClearsTheError() {
        var session = new ECOCraftConfirmSession();
        session.begin(false);
        session.observeResult(new Object(), version -> built(version, PlanningStatus.MISSING_ITEMS, true));
        long first = session.drain().report.version;

        session.fail(ECOCraftConfirmError.NO_CPU_ACCEPTED, "detail");
        var failed = session.drain();
        assertEquals(ECOCraftConfirmError.NO_CPU_ACCEPTED, failed.state.error);
        assertEquals("detail", failed.state.errorDetail);
        assertNull(failed.report);

        session.clearError();
        assertNull(session.drain().state.error);

        session.fail(ECOCraftConfirmError.SUBMIT_FAILED, "x");
        session.observeResult(new Object(), version -> built(version, PlanningStatus.SUCCESS, false));
        var replanned = session.drain();
        assertNull(replanned.state.error);
        assertNotNull(replanned.report);
        assertNotEquals(first, replanned.report.version);
    }

    @Test
    void planningFailureBecomesAnInternalErrorReport() {
        var session = new ECOCraftConfirmSession();
        session.begin(false);
        session.drain();
        session.planningFailed(() -> "boom");
        var failed = session.drain();
        assertTrue(failed.state.reportReady);
        assertEquals(PlanningStatus.INTERNAL_ERROR, failed.state.status);
        assertEquals("boom", failed.state.diagnostic);
        assertNotNull(failed.report);
        assertNotNull(
            ECOCraftConfirmRules.rejectStart(failed.state, true),
            "A failed planning attempt can never be started");
    }

    @Test
    void stateAndReportSurviveTheWire() {
        var original = built(7L, PlanningStatus.MISSING_ITEMS, true);
        var state = original.state.withCpuTier(6)
            .withError(ECOCraftConfirmError.FORCE_REJECTED, "why");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        state.write(buffer);
        original.report.write(buffer);

        var decodedState = ECOCraftConfirmState.read(buffer);
        var decodedReport = ECOCraftConfirmReport.read(buffer);
        assertEquals(state, decodedState);
        assertEquals(7L, decodedReport.version);
        assertEquals(1, decodedReport.materials.size());
        var row = decodedReport.materials.get(0);
        assertEquals(TestAddonKey.INSTANCE, row.key);
        assertEquals(BigInteger.valueOf(3), row.stored);
        assertEquals(BigInteger.ONE, row.missing);
        assertEquals(BigInteger.valueOf(2), row.craft);
        assertTrue(decodedReport.hasMissing());
        assertFalse(buffer.isReadable());
    }

    @Test
    void bigOrderCpuAvailabilityResendsOnlyTheHeaderAndSurvivesTheWire() {
        var session = new ECOCraftConfirmSession();
        session.begin(false);
        session.observeResult(
            new Object(),
            version -> built(version, PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false));
        assertFalse(session.drain().state.bigOrderCpuAvailable);

        session.setBigOrderCpu(true);
        var available = session.drain();
        assertNotNull(available);
        assertTrue(available.state.bigOrderCpuAvailable);
        assertNull(available.report, "A CPU change resends only the header");
        assertNull(session.drain());

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        available.state.withError(ECOCraftConfirmError.BIG_ORDER_NO_CPU, "NO_ELIGIBLE_ECO_CPU")
            .write(buffer);
        var decoded = ECOCraftConfirmState.read(buffer);
        assertTrue(decoded.bigOrderCpuAvailable);
        assertEquals(ECOCraftConfirmError.BIG_ORDER_NO_CPU, decoded.error);
        assertEquals("NO_ELIGIBLE_ECO_CPU", decoded.errorDetail);
        assertFalse(buffer.isReadable());
    }

    @Test
    void openPageAdoptsABetterPlanFromTheBackgroundSearch() {
        var improvements = mock(ECOPlanningImprovements.class);
        var displayed = mock(ECOPlanningResult.class);
        var better = mock(ECOPlanningResult.class);
        when(displayed.improvements()).thenReturn(improvements);
        when(better.improvements()).thenReturn(improvements);
        when(improvements.latest(displayed)).thenReturn(null);

        var session = new ECOCraftConfirmSession();
        assertNull(session.improvement(handle -> true), "A native confirmation never adopts anything");
        session.begin(false);
        session.observeResult(new Object(), version -> {
            session.display(displayed);
            return built(version, PlanningStatus.SUCCESS, false);
        });
        long first = session.drain().report.version;
        assertNull(session.improvement(handle -> true), "Nothing better yet");

        when(improvements.latest(displayed)).thenReturn(better);
        assertSame(better, session.improvement(handle -> true));

        // The tracker hands the better plan to AE2; its new result rebuilds the report under a new version.
        session.observeResult(new Object(), version -> {
            session.display(better);
            return built(version, PlanningStatus.SUCCESS, false);
        });
        var replanned = session.drain();
        assertNotNull(replanned.report);
        assertNotEquals(first, replanned.report.version);
        assertEquals(replanned.state.reportVersion, replanned.report.version);
        assertSame(better, session.displayed());
        verify(improvements, never()).close();
    }

    @Test
    void searchesOfAnotherGridAreClosedInsteadOfAdopted() {
        var improvements = mock(ECOPlanningImprovements.class);
        var displayed = mock(ECOPlanningResult.class);
        when(displayed.improvements()).thenReturn(improvements);
        when(improvements.latest(displayed)).thenReturn(mock(ECOPlanningResult.class));

        var session = new ECOCraftConfirmSession();
        session.begin(false);
        session.display(displayed);
        assertNull(session.improvement(handle -> false));
        verify(improvements).close();
    }

    @Test
    void startOrCloseFreezesTheDisplayedPlanAndCancelsTheSearch() {
        var improvements = mock(ECOPlanningImprovements.class);
        var displayed = mock(ECOPlanningResult.class);
        when(displayed.improvements()).thenReturn(improvements);
        when(improvements.latest(displayed)).thenReturn(mock(ECOPlanningResult.class));

        var session = new ECOCraftConfirmSession();
        session.begin(false);
        session.display(displayed);
        session.closeImprovements();
        verify(improvements).close();
        assertNull(session.improvement(handle -> true), "A frozen page never swaps its plan");
        session.closeImprovements();
        verify(improvements, times(2)).close();

        var empty = new ECOCraftConfirmSession();
        empty.closeImprovements(); // No displayed plan: closing is a no-op.
        assertNull(empty.improvement(handle -> true));
    }

    @Test
    void absentStatusAndErrorSurviveTheWire() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ECOCraftConfirmReports.calculating(true)
            .write(buffer);
        var decoded = ECOCraftConfirmState.read(buffer);
        assertNull(decoded.status);
        assertNull(decoded.error);
        assertEquals(ECOCraftConfirmReports.calculating(true), decoded);
    }
}
