package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.CraftingAllow;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * CPU lifecycle and selection for ECO identities published in AE2's crafting cache, kept out of the mixin so the
 * rules are testable: release finished ECO CPUs every tick, route ECO plans to ECO pools first (preferred
 * selection mode first), and fall back to native AE2 CPUs for plans they can run unchanged, as 1.21 does.
 */
public final class NativeECOCpuSelection {

    private NativeECOCpuSelection() {}

    /**
     * Releases finished CPUs of every ECO owner with an identity in the published set; each owner once.
     *
     * @param visited reusable scratch set, cleared before returning
     * @return true when the published CPU list has to be rebuilt
     */
    public static boolean releaseFinished(Iterable<? extends ICraftingCPU> published, Set<Object> visited) {
        boolean changed = false;
        try {
            for (ICraftingCPU cpu : published) {
                if (!(cpu instanceof NativeECOCpu)) continue;
                NativeECOCpuRuntime runtime = ((NativeECOCpu) cpu).runtime();
                if (!visited.add(runtime.lifecycleOwner())) continue;
                if (runtime.releaseFinishedCpus()) changed = true;
            }
        } finally {
            visited.clear();
        }
        return changed;
    }

    /**
     * Whether AE2's own submission path may handle this ECO job unchanged: only an explicit native target for a
     * plan that native CPUs can execute. Every other ECO submission is answered by {@link #submit}.
     */
    public static boolean deferToNative(NativeECOCraftingJob job, @Nullable ICraftingCPU target, IGrid grid) {
        return target instanceof CraftingCPUCluster && !(target instanceof NativeECOCpu)
            && job.supportsNativeCpus()
            && job.belongsToGrid(grid);
    }

    @Nullable
    public static ICraftingLink submit(Iterable<CraftingCPUCluster> published, IGrid grid, NativeECOCraftingJob job,
        @Nullable ICraftingRequester requester, @Nullable ICraftingCPU target, boolean prioritizePower,
        BaseActionSource source, boolean follow) {
        if (job.isSimulation()) return null;
        if (target != null && !(target instanceof NativeECOCpu)) return null;
        List<NativeECOCpu> candidates = new ArrayList<>();
        for (CraftingCPUCluster cpu : published) if (cpu instanceof NativeECOCpu && (target == null || target == cpu)
            && cpu.isActive()
            && !cpu.isBusy()
            && ((NativeECOCpu) cpu).runtime()
                .hasFreeThread()
            && cpu.getAvailableStorage() >= job.getByteTotal()
            && allows(cpu, source)) candidates.add((NativeECOCpu) cpu);
        if (!candidates.isEmpty()) {
            Comparator<NativeECOCpu> order = Comparator.comparingInt(NativeECOCpu::getCoProcessors)
                .thenComparingLong(NativeECOCpu::getAvailableStorage);
            if (prioritizePower) order = order.reversed();
            candidates.sort(
                Comparator.comparing(
                    (NativeECOCpu cpu) -> !cpu.runtime()
                        .isPreferredFor(source))
                    .thenComparing(order));
            // Identities of one pool (placeholder, members, finished CPUs) submit into the same cluster: try it once.
            Set<Object> pools = Collections.newSetFromMap(new IdentityHashMap<>());
            for (NativeECOCpu cpu : candidates) {
                if (!pools.add(
                    cpu.runtime()
                        .submissionPool()))
                    continue;
                ICraftingLink link = cpu.submitJob(grid, job, source, requester);
                if (link != null) {
                    follow(cpu, source, follow);
                    return link;
                }
            }
            return null;
        }
        // 1.21: with no ECO CPU able to take it, a plan native CPUs can run falls back to them.
        if (target != null || !job.supportsNativeCpus() || !job.belongsToGrid(grid)) return null;
        List<CraftingCPUCluster> natives = new ArrayList<>();
        for (CraftingCPUCluster cpu : published) if (!(cpu instanceof NativeECOCpu) && cpu.isActive()
            && !cpu.isBusy()
            && cpu.getAvailableStorage() >= job.getByteTotal()
            && allows(cpu, source)) natives.add(cpu);
        if (natives.isEmpty()) return null;
        // AE2's own order: fewest co-processors first, most when power is prioritised; it tries only the best.
        Comparator<CraftingCPUCluster> order = Comparator.comparingInt(CraftingCPUCluster::getCoProcessors)
            .thenComparingLong(CraftingCPUCluster::getAvailableStorage);
        if (prioritizePower) order = order.reversed();
        natives.sort(order);
        CraftingCPUCluster selected = natives.get(0);
        ICraftingLink link = selected.submitJob(grid, job, source, requester);
        if (link != null) follow(selected, source, follow);
        return link;
    }

    /** The CPU's selection mode accepts a job requested by this source. */
    public static boolean allows(CraftingCPUCluster cpu, BaseActionSource source) {
        return source.isPlayer() ? cpu.getCraftingAllowMode() != CraftingAllow.ONLY_NONPLAYER
            : cpu.getCraftingAllowMode() != CraftingAllow.ONLY_PLAYER;
    }

    private static void follow(CraftingCPUCluster cpu, BaseActionSource source, boolean follow) {
        if (follow && source instanceof PlayerSource)
            cpu.togglePlayerFollowStatus(((PlayerSource) source).player.getCommandSenderName());
    }
}
