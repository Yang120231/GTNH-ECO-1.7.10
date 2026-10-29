package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.container.implementations.ContainerCraftConfirm;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCraftingJob;
import cn.dancingsnow.neoecoae.platform.mixin.ContainerCraftConfirmAccessor;
import cn.dancingsnow.neoecoae.platform.network.ECOCraftConfirmPackets;

class CraftSubmissionFailureTest {

    @AfterEach
    void closeSessions() {
        ECOCraftConfirmTracker.clear();
    }

    @Test
    void ordinaryAndBigOrderLinkageFailuresReturnAnErrorAndDisableAutomaticSubmission() throws Exception {
        for (boolean big : new boolean[] { false, true }) for (Throwable failure : new Throwable[] {
            new IllegalStateException("runtime failure"), new NoSuchMethodError("missing provider method") }) {
                var player = mock(EntityPlayerMP.class);
                var menu = mock(
                    ContainerCraftConfirm.class,
                    withSettings().extraInterfaces(ContainerCraftConfirmAccessor.class));
                menu.windowId = 17;
                player.openContainer = menu;
                var job = mock(NativeECOCraftingJob.class);
                var plan = mock(ICraftingPlan.class);
                when(job.sourcePlan()).thenReturn(plan);
                when(((ContainerCraftConfirmAccessor) menu).neoecoae$getResult()).thenReturn(job);
                Class<?> trackedType = Class.forName(ECOCraftConfirmTracker.class.getName() + "$Tracked");
                var constructor = trackedType.getDeclaredConstructor(ContainerCraftConfirm.class);
                constructor.setAccessible(true);
                Object tracked = constructor.newInstance(menu);
                var field = trackedType.getDeclaredField("session");
                field.setAccessible(true);
                var session = (ECOCraftConfirmSession) field.get(tracked);
                session.begin(false);
                session.observeResult(
                    job,
                    version -> new ECOCraftConfirmReports.Built(
                        new ECOCraftConfirmState(
                            true,
                            true,
                            false,
                            false,
                            0,
                            BigInteger.ONE,
                            big ? PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE : PlanningStatus.SUCCESS,
                            "",
                            0,
                            false,
                            1,
                            version,
                            null,
                            ""),
                        ECOCraftConfirmReport.EMPTY));
                var table = ECOCraftConfirmTracker.class.getDeclaredField("TRACKED");
                table.setAccessible(true);
                ((Map<EntityPlayerMP, Object>) table.get(null)).put(player, tracked);
                var exact = mock(ECOPlanningResult.class);
                session.display(exact);
                try (var channels = mockConstruction(cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper.class);
                    var packets = mockStatic(ECOCraftConfirmPackets.class);
                    var reports = mockStatic(ECOCraftConfirmReports.class)) {
                    reports.when(() -> ECOCraftConfirmReports.resolve(plan))
                        .thenReturn(exact);
                    if (big) {
                        when(menu.getGrid()).thenThrow(failure);
                        assertDoesNotThrow(() -> ECOCraftConfirmTracker.startBigOrder(player, 17, false));
                    } else {
                        var grid = mock(IGrid.class);
                        when(menu.getGrid()).thenReturn(grid);
                        when(grid.getCache(ICraftingGrid.class)).thenThrow(failure);
                        assertDoesNotThrow(() -> ECOCraftConfirmTracker.start(player, 17, false));
                    }
                    assertEquals(ECOCraftConfirmError.SUBMIT_FAILED, session.state().error);
                    assertTrue(session.state().errorDetail.contains(failure.getMessage()));
                    verify(menu, atLeastOnce()).setAutoStart(false);
                    verify(menu).setAutoStartAndFollow(false);
                    verify(menu, never()).switchToOriginalGUI();
                    packets.verify(
                        () -> ECOCraftConfirmPackets.sync(
                            eq(player),
                            eq(17),
                            argThat(state -> state.error == ECOCraftConfirmError.SUBMIT_FAILED),
                            any()));
                }
                ECOCraftConfirmTracker.clear();
            }
    }
}
