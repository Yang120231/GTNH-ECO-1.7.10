package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ObjLongConsumer;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.crafting.fast.CraftingJobFast;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.resolvers.CraftableItemResolver;
import appeng.crafting.v2.resolvers.CraftingTask;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.AEKeyFilter;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/**
 * A plan AE2's own planner made, in the numerical plan form {@code /neoecoae export_plan} reads: the stock and missing
 * amounts of the ECO page's native rows and, where the job exposes them, its pattern executions keyed by the
 * network's planning views. Read-only: nothing is staged, extracted or submitted.
 */
final class NativePlanExport {

    /** AE2's lite planner; absent from older rv3 releases, so it is matched by name before its reader loads. */
    private static final String LITE_JOB = "appeng.crafting.fast.CraftingJobFast";

    final ICraftingPlan plan;
    /** False when the job type does not expose its pattern executions, or one of them cannot be represented. */
    final boolean tasksKnown;

    private NativePlanExport(ICraftingPlan plan, boolean tasksKnown) {
        this.plan = plan;
        this.tasksKnown = tasksKnown;
    }

    /**
     * @param patterns the network's planning views, so native tasks carry the pattern ids the export lists
     * @return {@code null} when the job's output has no ECO key
     */
    static @Nullable NativePlanExport of(ICraftingJob<?> job, BaseActionSource source, ICraftingService patterns,
        World world) {
        AEKey output = AEKey.fromNative(job.getOutput());
        if (output == null) return null;
        KeyCounter used = new KeyCounter(), missing = new KeyCounter();
        for (ECOCraftConfirmReport.Row row : NativeCraftConfirmReports.rows(job, source)) {
            // Rows come from native long amounts, so the projections are exact.
            if (row.stored.signum() > 0) used.add(row.key, row.stored.longValue());
            if (row.missing.signum() > 0) missing.add(row.key, row.missing.longValue());
        }
        Map<ICraftingPatternDetails, IPatternDetails> views = new IdentityHashMap<>();
        for (AEKey key : patterns.getCraftables(AEKeyFilter.none()))
            for (IPatternDetails view : patterns.getCraftingFor(key))
                if (view instanceof NativePatternDetails captured) views.putIfAbsent(captured.nativeDetails(), view);
        Map<IPatternDetails, Long> tasks = new LinkedHashMap<>();
        boolean[] representable = { true };
        boolean known = nativeTasks(job, (pattern, times) -> {
            IPatternDetails view = views.get(pattern);
            if (view == null) {
                try {
                    view = NativePatternDetails.capture(pattern, world, Collections.emptyList());
                } catch (IllegalArgumentException unrepresentable) {
                    representable[0] = false;
                    return;
                }
                views.put(pattern, view);
            }
            tasks.merge(view, times, Math::addExact);
        });
        CraftingPlan plan = new CraftingPlan(
            new GenericStack(
                output,
                job.getOutput()
                    .getStackSize()),
            job.getByteTotal(),
            job.isSimulation(),
            false,
            used,
            new KeyCounter(),
            missing,
            tasks);
        return new NativePlanExport(plan, known && representable[0]);
    }

    /** Pattern executions of the job, as the native plan tree reads them; false when the job hides them. */
    @SuppressWarnings({ "rawtypes" })
    private static boolean nativeTasks(ICraftingJob<?> job, ObjLongConsumer<ICraftingPatternDetails> sink) {
        if (job instanceof CraftingJobV2 v2) {
            CraftingContext context = v2.getContext();
            if (context == null) return false;
            for (CraftingTask task : context.getResolvedTasks()) {
                if (!(task instanceof CraftableItemResolver.CraftFromPatternTask craft)) continue;
                long done = craft.getTotalCraftsDone();
                if (done > 0 && craft.pattern != null) sink.accept(craft.pattern, done);
            }
            return true;
        }
        return LITE_JOB.equals(
            job.getClass()
                .getName())
            && LiteJob.tasks(job, sink);
    }

    /** Only loaded for a lite job, so a runtime without AE2's lite planner never links it. */
    private static final class LiteJob {

        static boolean tasks(ICraftingJob<?> job, ObjLongConsumer<ICraftingPatternDetails> sink) {
            ((CraftingJobFast<?>) job)
                .forEachPattern((pattern, times) -> { if (pattern != null && times > 0) sink.accept(pattern, times); });
            return true;
        }
    }
}
