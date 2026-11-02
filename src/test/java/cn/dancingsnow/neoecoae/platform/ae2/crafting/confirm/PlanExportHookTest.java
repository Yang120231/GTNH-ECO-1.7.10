package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.container.implementations.ContainerCraftConfirm;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.command.CraftingPlanExport;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCraftingJob;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

/** The confirmation hook of {@code /neoecoae export_plan}: armed exports only, once per calculated plan. */
class PlanExportHookTest {

    @Test
    void onlyAnArmedPlayersNextCalculatedPlanIsExportedOnce() throws Exception {
        var player = mock(EntityPlayerMP.class);
        var menu = mock(ContainerCraftConfirm.class);
        var grid = mock(IGrid.class);
        when(menu.getGrid()).thenReturn(grid);
        var options = new ECOPlannerOptions(true, false, JdkCompat.setOf());
        Object tracked = tracked(menu, options);
        var shown = ecoJob(mock(ICraftingPlan.class));
        var plan = mock(ICraftingPlan.class);
        var next = ecoJob(plan);
        var result = mock(ECOPlanningResult.class);

        try (var export = mockStatic(CraftingPlanExport.class);
            var reports = mockStatic(ECOCraftConfirmReports.class)) {
            reports.when(() -> ECOCraftConfirmReports.resolve(plan))
                .thenReturn(result);
            // An ordinary confirmation is only compared by identity; nothing is resolved or written.
            hook(player, tracked, shown);
            reports.verify(() -> ECOCraftConfirmReports.resolve(any()), never());

            export.when(() -> CraftingPlanExport.isArmed(player))
                .thenReturn(true);
            // Arming after a plan is shown waits for the next calculation, like 1.21's summary hook.
            hook(player, tracked, shown);
            export.verify(() -> CraftingPlanExport.onPlanReady(any(), any(), any(), any(), any()), never());

            hook(player, tracked, next);
            hook(player, tracked, next);
            export.verify(
                () -> CraftingPlanExport.onPlanReady(player, GridView.of(grid), plan, result, options),
                times(1));
        }
    }

    @Test
    void anUnreadableNativePlanDisarmsWithAReason() throws Exception {
        var player = mock(EntityPlayerMP.class);
        var menu = mock(ContainerCraftConfirm.class);
        // No ECO pattern view is registered on this grid, so AE2's plan cannot be mapped to pattern ids.
        when(menu.getGrid()).thenReturn(mock(IGrid.class));
        Object tracked = tracked(menu, null);
        try (var export = mockStatic(CraftingPlanExport.class)) {
            export.when(() -> CraftingPlanExport.isArmed(player))
                .thenReturn(true);
            hook(player, tracked, mock(ICraftingJob.class));
            export.verify(() -> CraftingPlanExport.onPlanUnreadable(eq(player), any(IllegalStateException.class)));
            export.verify(() -> CraftingPlanExport.onNativePlanReady(any(), any(), any(), anyBoolean()), never());
        }
    }

    private static NativeECOCraftingJob ecoJob(ICraftingPlan plan) {
        var job = mock(NativeECOCraftingJob.class);
        when(job.sourcePlan()).thenReturn(plan);
        return job;
    }

    private static Object tracked(ContainerCraftConfirm menu, ECOPlannerOptions options) throws Exception {
        Class<?> type = Class.forName(ECOCraftConfirmTracker.class.getName() + "$Tracked");
        Constructor<?> constructor = type.getDeclaredConstructor(ContainerCraftConfirm.class);
        constructor.setAccessible(true);
        Object tracked = constructor.newInstance(menu);
        Field field = type.getDeclaredField("options");
        field.setAccessible(true);
        field.set(tracked, options);
        return tracked;
    }

    private static void hook(EntityPlayerMP player, Object tracked, ICraftingJob<?> result) throws Exception {
        Method hook = ECOCraftConfirmTracker.class
            .getDeclaredMethod("exportPlanIfArmed", EntityPlayerMP.class, tracked.getClass(), ICraftingJob.class);
        hook.setAccessible(true);
        hook.invoke(null, player, tracked, result);
    }
}
