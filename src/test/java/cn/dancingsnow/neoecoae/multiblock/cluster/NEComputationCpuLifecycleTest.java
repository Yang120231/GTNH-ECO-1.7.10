package cn.dancingsnow.neoecoae.multiblock.cluster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.config.CraftingAllow;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkEvent;
import appeng.api.networking.security.BaseActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingNativeRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitErrorCode;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpu;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpuSelection;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCraftingJob;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** CPU release, native identity bookkeeping and submission routing of a computation host. */
class NEComputationCpuLifecycleTest {

    private IGrid grid;
    private NEComputationCluster cluster;
    private ECOComputationThreadingCoreBlockEntity core;
    private final List<ECOCraftingCPU> spawned = new ArrayList<>();
    private final List<AtomicBoolean> running = new ArrayList<>();
    private int freeThreads = 1;

    @BeforeEach
    void setUp() {
        grid = mock(IGrid.class);
        IGridNode node = mock(IGridNode.class);
        when(node.getGrid()).thenReturn(grid);
        when(node.isActive()).thenReturn(true);
        var controller = mock(ECOComputationSystemBlockEntity.class);
        when(controller.getActionableNode()).thenReturn(node);

        cluster = spy(new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO));
        doReturn(100L).when(cluster)
            .getTotalStorage();
        doReturn(1).when(cluster)
            .getMaxThreads();
        doAnswer(call -> identity(call.getArgument(0))).when(cluster)
            .createNativeCPU(any());
        cluster.addBlockEntity(controller);

        core = mock(ECOComputationThreadingCoreBlockEntity.class);
        when(core.spawn(any())).thenAnswer(call -> freeThreads-- > 0 ? newCpu(call.getArgument(0)) : null);
        doAnswer(call -> {
            freeThreads++;
            return null;
        }).when(core)
            .deactivate(any());
        cluster.addBlockEntity(core);
        cluster.recalculateRemainingStorage();
    }

    @Test
    void finishedJobReleasesThreadBytesIdentityAndRepublishesPlaceholder() {
        ICraftingPlan plan = plan(40);
        assertTrue(submit(plan).successful());
        ECOCraftingCPU cpu = spawned.get(0);
        NativeECOCpu identity = (NativeECOCpu) cluster.nativeCPU(cpu);
        assertEquals(60L, cluster.getAvailableStorage());
        assertEquals(1, cluster.getActiveCPUCount());
        assertFalse(cluster.shouldPublishPlaceholder(), "A busy single-thread host has no free placeholder");
        assertTrue(cluster.hasNativeCPU(cpu));

        // A second identity of the same host in AE2's published set: the host is pruned once per tick.
        NativeECOCpu placeholder = (NativeECOCpu) cluster.nativeCPU(cluster.getFakeCPU());
        Set<Object> scratch = Collections.newSetFromMap(new IdentityHashMap<>());
        clearInvocations(grid, cluster);
        assertFalse(NativeECOCpuSelection.releaseFinished(Arrays.asList(identity, placeholder), scratch));
        verify(grid, never()).postEvent(any(MENetworkEvent.class));

        clearInvocations(cluster);
        running.get(0)
            .set(false); // job finished successfully on its own
        assertTrue(NativeECOCpuSelection.releaseFinished(Arrays.asList(identity, placeholder), scratch));
        assertTrue(scratch.isEmpty());
        verify(cluster, times(1)).pruneInactiveCPUs();
        verify(core).deactivate(cpu);
        assertEquals(100L, cluster.getAvailableStorage());
        assertEquals(0, cluster.getActiveCPUCount());
        assertFalse(cluster.isPlanRegistered(plan));
        assertFalse(cluster.hasNativeCPU(cpu));
        verify(grid).postEvent(isA(MENetworkCraftingCpuChange.class));
        assertTrue(cluster.shouldPublishPlaceholder(), "The released thread must be advertised again");

        clearInvocations(grid);
        assertFalse(cluster.pruneInactiveCPUs(), "Releasing is idempotent");
        verify(grid, never()).postEvent(any(MENetworkEvent.class));

        assertTrue(submit(plan(40)).successful(), "The freed thread accepts the next job");
    }

    @Test
    void placeholderIsPublishedWithoutFreeBytesWhileAThreadIsFree() {
        doReturn(0L).when(cluster)
            .getTotalStorage();
        cluster.recalculateRemainingStorage();
        assertEquals(0L, cluster.getAvailableStorage());
        assertTrue(cluster.shouldPublishPlaceholder());
    }

    @Test
    void killedCpusNeverLeakNativeIdentities() {
        ICraftingPlan cancelled = plan(40);
        assertTrue(submit(cancelled).successful());
        ECOCraftingCPU first = spawned.get(0);
        cluster.nativeCPU(first);
        cluster.cancelJob(cancelled);
        assertFalse(cluster.hasNativeCPU(first));
        assertFalse(cluster.isPlanRegistered(cancelled));
        verify(core).deactivate(first);

        ICraftingPlan evicted = plan(40);
        assertTrue(submit(evicted).successful());
        ECOCraftingCPU second = spawned.get(1);
        cluster.nativeCPU(second);
        doReturn(10L).when(cluster)
            .getTotalStorage(); // drives removed: reservations no longer fit
        cluster.recalculateRemainingStorage();
        assertFalse(cluster.hasNativeCPU(second));
        assertFalse(cluster.isPlanRegistered(evicted));

        // An identity created for a CPU that never got registered (failed restore) is swept on the next prune.
        ECOCraftingCPU orphan = newCpu(plan(1));
        cluster.nativeCPU(orphan);
        assertTrue(cluster.hasNativeCPU(orphan));
        assertTrue(cluster.pruneInactiveCPUs());
        assertFalse(cluster.hasNativeCPU(orphan));
    }

    @Test
    void staleCpuIdentitySubmitsThroughTheClusterNeverIntoItsOwnLogic() {
        assertTrue(submit(plan(40)).successful());
        ECOCraftingCPU stale = spawned.get(0);
        running.get(0)
            .set(false); // finished, not yet released: still listed by AE2 as an idle CPU
        when(stale.getGrid()).thenReturn(GridView.of(grid));

        ICraftingPlan next = plan(40);
        new ECOCraftingNativeRuntime(stale).submit(next, new BaseActionSource(), null);

        verify(stale.getLogic(), times(1)).trySubmitJob(any(), any(), any(), any());
        verify(cluster).submitJob(any(), same(next), any(), any());
        ECOCraftingCPU fresh = spawned.get(1);
        assertNotSame(stale, fresh);
        assertSame(fresh, cluster.findCPU(next));
        assertFalse(cluster.hasNativeCPU(stale), "Submitting released the finished CPU first");
        verify(core).deactivate(stale);
    }

    @Test
    void submitReportsNoCpuFoundWhenEveryThreadIsBusy() {
        assertTrue(submit(plan(10)).successful());
        var result = submit(plan(10));
        assertFalse(result.successful());
        assertEquals(CraftingSubmitErrorCode.NO_CPU_FOUND, result.errorCode());
    }

    @Test
    void ecoPlansFallBackToNativeCpusOnlyWhenNoEcoCpuCanTakeThem() {
        NativeECOCpu eco = mock(NativeECOCpu.class);
        var runtime = mock(cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu.NativeECOCpuRuntime.class);
        when(eco.runtime()).thenReturn(runtime);
        when(eco.isActive()).thenReturn(true);
        when(eco.getAvailableStorage()).thenReturn(1000L);
        when(eco.getCraftingAllowMode()).thenReturn(CraftingAllow.ALLOW_ALL);
        when(runtime.submissionPool()).thenReturn(runtime);
        CraftingCPUCluster vanilla = mock(CraftingCPUCluster.class);
        when(vanilla.isActive()).thenReturn(true);
        when(vanilla.getAvailableStorage()).thenReturn(1000L);
        when(vanilla.getCraftingAllowMode()).thenReturn(CraftingAllow.ALLOW_ALL);
        ICraftingLink link = mock(ICraftingLink.class);
        NativeECOCraftingJob job = mock(NativeECOCraftingJob.class);
        when(job.getByteTotal()).thenReturn(10L);
        when(job.belongsToGrid(grid)).thenReturn(true);
        when(job.supportsNativeCpus()).thenReturn(true);
        BaseActionSource source = mock(BaseActionSource.class);
        when(vanilla.submitJob(grid, job, source, null)).thenReturn(link);
        when(eco.submitJob(grid, job, source, null)).thenReturn(link);
        List<CraftingCPUCluster> published = Arrays.asList(eco, vanilla);

        // An ECO pool with a free thread takes the plan.
        when(runtime.hasFreeThread()).thenReturn(true);
        assertSame(link, NativeECOCpuSelection.submit(published, grid, job, null, null, false, source, false));
        verify(vanilla, never()).submitJob(any(), any(), any(), any());

        // Every ECO thread busy: a native-safe plan runs on AE2's CPU.
        when(runtime.hasFreeThread()).thenReturn(false);
        assertSame(link, NativeECOCpuSelection.submit(published, grid, job, null, null, false, source, false));
        verify(vanilla).submitJob(grid, job, source, null);

        // Plans that need the ECO runtime never fall back.
        clearInvocations(vanilla);
        when(job.supportsNativeCpus()).thenReturn(false);
        assertNull(NativeECOCpuSelection.submit(published, grid, job, null, null, false, source, false));
        verify(vanilla, never()).submitJob(any(), any(), any(), any());
        assertFalse(NativeECOCpuSelection.deferToNative(job, vanilla, grid));
        when(job.supportsNativeCpus()).thenReturn(true);
        assertTrue(NativeECOCpuSelection.deferToNative(job, vanilla, grid));
        assertFalse(NativeECOCpuSelection.deferToNative(job, eco, grid));
    }

    private CraftingSubmitResult submit(ICraftingPlan plan) {
        return (CraftingSubmitResult) cluster.submitJob(GridView.of(grid), plan, IActionSource.empty(), null);
    }

    private static ICraftingPlan plan(long bytes) {
        ICraftingPlan plan = mock(ICraftingPlan.class);
        when(plan.bytes()).thenReturn(bytes);
        return plan;
    }

    private ECOCraftingCPU newCpu(ICraftingPlan plan) {
        ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        ECOCraftingCPULogic logic = mock(ECOCraftingCPULogic.class);
        AtomicBoolean active = new AtomicBoolean();
        when(cpu.getLogic()).thenReturn(logic);
        when(cpu.getOwner()).thenReturn(core);
        when(cpu.getPlan()).thenReturn(plan);
        when(cpu.getCluster()).thenReturn(cluster);
        when(logic.hasJob()).thenAnswer(call -> active.get());
        when(logic.trySubmitJob(any(), any(), any(), any())).thenAnswer(call -> {
            active.set(true);
            return CraftingSubmitResult.successful(null);
        });
        spawned.add(cpu);
        running.add(active);
        return cpu;
    }

    private static NativeECOCpu identity(ECOCraftingCPU cpu) {
        NativeECOCpu identity = mock(NativeECOCpu.class);
        when(identity.runtime()).thenReturn(new ECOCraftingNativeRuntime(cpu));
        return identity;
    }
}
