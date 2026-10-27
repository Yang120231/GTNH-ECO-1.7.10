package cn.dancingsnow.neoecoae.platform.ui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderState;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;
import io.netty.buffer.Unpooled;

class NativeComputationSessionTest {

    private static ECOCraftingCPU cpu() {
        ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        when(cpu.isBusy()).thenReturn(true);
        when(cpu.getTier()).thenReturn(StorageTiers.L4);
        when(cpu.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
        when(cpu.getLogic()).thenReturn(mock(ECOCraftingCPULogic.class));
        ECOCraftingProgressView progress = mock(ECOCraftingProgressView.class);
        when(progress.bigOrder()).thenReturn(Optional.empty());
        when(cpu.getProgressView()).thenReturn(progress);
        return cpu;
    }

    private static ECOComputationSystemBlockEntity host(NEComputationCluster cluster) {
        ECOComputationSystemBlockEntity host = mock(ECOComputationSystemBlockEntity.class);
        when(host.getTier()).thenReturn(StorageTiers.L4);
        when(host.getCluster()).thenReturn(cluster);
        var node = mock(cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode.class);
        when(host.getMainNode()).thenReturn(node);
        when(node.isOnline()).thenReturn(true);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
        when(cluster.getDrives()).thenReturn(Collections.emptyList());
        when(cluster.getUpperDrives()).thenReturn(Collections.emptyList());
        when(cluster.getLowerDrives()).thenReturn(Collections.emptyList());
        return host;
    }

    @Test
    void standaloneStatusCountsTheCurrentHostAndKeepsEveryBlocker() {
        var cluster = mock(NEComputationCluster.class);
        when(cluster.getEndgameBlockers()).thenReturn(Arrays.asList("host_count", "tier", "max_length", "drives"));
        var state = new NativeComputationSession(host(cluster)).capture();
        assertFalse(state.getBoolean("endgameActive"));
        assertEquals(1, state.getInt("networkHosts"));
        var blockers = state.getList("endgameBlockers", 8);
        assertEquals(4, blockers.size());
        assertEquals("host_count", blockers.getString(0));
        assertEquals("tier", blockers.getString(1));
        assertEquals("max_length", blockers.getString(2));
        assertEquals("drives", blockers.getString(3));
    }

    @Test
    void infiniteStatusTracksNetworkConditionsInsteadOfDisplayedCapacity() {
        var cluster = mock(NEComputationCluster.class);
        when(cluster.getNetworkMemberCount()).thenReturn(8);
        when(cluster.getPooledTotalStorage()).thenReturn(Long.MAX_VALUE);
        when(cluster.getPooledParallelism()).thenReturn(Integer.MAX_VALUE);
        when(cluster.getEndgameBlockers()).thenReturn(Collections.singletonList("drives"));
        var session = new NativeComputationSession(host(cluster));
        var blocked = session.capture();
        assertFalse(blocked.getBoolean("endgameActive"));
        assertEquals(8, blocked.getInt("networkHosts"));
        assertEquals("drives", blocked.getList("endgameBlockers", 8).getString(0));
        when(cluster.getEndgameBlockers()).thenReturn(Collections.emptyList());
        var active = session.capture();
        assertTrue(active.getBoolean("endgameActive"));
        assertTrue(active.getList("endgameBlockers", 8).isEmpty());
        when(cluster.getEndgameBlockers()).thenReturn(Collections.singletonList("high_energy_switch"));
        var changed = session.capture();
        assertFalse(changed.getBoolean("endgameActive"));
        assertEquals("high_energy_switch", changed.getList("endgameBlockers", 8).getString(0));
    }

    @Test
    void removingTheClusterClearsInfiniteStatusAndRestoresStandaloneCount() {
        var cluster = mock(NEComputationCluster.class);
        when(cluster.getNetworkMemberCount()).thenReturn(8);
        when(cluster.getEndgameBlockers()).thenReturn(Collections.emptyList());
        var controller = host(cluster);
        var session = new NativeComputationSession(controller);
        assertTrue(session.capture().getBoolean("endgameActive"));
        when(controller.getCluster()).thenReturn(null);
        var removed = session.capture();
        assertFalse(removed.getBoolean("endgameActive"));
        assertEquals(1, removed.getInt("networkHosts"));
        assertEquals(1, removed.getList("endgameBlockers", 8).size());
        assertEquals("host_count", removed.getList("endgameBlockers", 8).getString(0));
    }

    @Test
    void reorderingTaskListCannotRedirectPauseOrCancellation() {
        NEComputationCluster cluster = mock(NEComputationCluster.class);
        ECOCraftingCPU first = cpu(), second = cpu();
        ArrayList<ECOCraftingCPU> active = new ArrayList<>(Arrays.asList(first, second));
        when(cluster.getActiveCPUs()).thenReturn(active);
        NativeComputationSession session = new NativeComputationSession(host(cluster));
        int token = session.capture()
            .getList("cpus", 10)
            .getCompound(0)
            .getInt("token");
        Collections.reverse(active);
        session.apply(6, token);
        verify(first.getLogic()).setJobSuspended(true);
        verify(second.getLogic(), never()).setJobSuspended(anyBoolean());
        active.remove(first);
        session.apply(7, token);
        verify(first, never()).cancelJob();
        verify(second, never()).cancelJob();
    }

    @Test
    void bigOrderPreviewKeepsExactAmountsBeyondLong() {
        NEComputationCluster cluster = mock(NEComputationCluster.class);
        ECOCraftingCPU cpu = cpu();
        when(cluster.getActiveCPUs()).thenReturn(Collections.singletonList(cpu));
        BigInteger requested = BigInteger.TEN.pow(40)
            .add(BigInteger.valueOf(13));
        ECOBigOrderProgress order = new ECOBigOrderProgress(
            UUID.randomUUID(),
            ECOBigOrderState.RUNNING_CHILD,
            requested,
            BigInteger.ONE,
            requested.subtract(BigInteger.ONE),
            0,
            0,
            "");
        when(
            cpu.getProgressView()
                .bigOrder()).thenReturn(Optional.of(order));
        NativeComputationSession session = new NativeComputationSession(host(cluster));
        var row = session.capture()
            .getList("cpus", 10)
            .getCompound(0);
        assertEquals(requested.toString(), row.getString("requested"));
        assertEquals(
            requested.subtract(BigInteger.ONE)
                .toString(),
            row.getString("remaining"));
    }

    @Test
    void malformedActionPayloadIsRejectedBeforeQueuingWorldEdits() {
        NativeComputationSession session = new NativeComputationSession(host(mock(NEComputationCluster.class)));
        PacketBuffer bytes = new PacketBuffer(Unpooled.buffer());
        try {
            bytes.writeInt(7);
            assertThrows(java.io.IOException.class, () -> session.readOnServer(0, bytes));
        } finally {
            bytes.release();
        }
    }

    @Test
    void pooledCpuCanBePausedAndDisappearingNetworkMembershipInvalidatesItsToken() {
        var local = mock(NEComputationCluster.class);
        var remote = mock(NEComputationCluster.class);
        var network = mock(cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster.class);
        var running = cpu();
        when(local.getNetworkCluster()).thenReturn(network);
        when(network.getMembers()).thenReturn(Arrays.asList(local, remote));
        when(local.getActiveCPUs()).thenReturn(Collections.emptyList());
        when(remote.getActiveCPUs()).thenReturn(Collections.singletonList(running));
        when(remote.isActive()).thenReturn(true);
        var session = new NativeComputationSession(host(local));
        int token = session.capture().getList("cpus", 10).getCompound(0).getInt("token");
        session.apply(6, token);
        verify(running.getLogic()).setJobSuspended(true);
        when(network.getMembers()).thenReturn(Collections.singletonList(local));
        session.apply(7, token);
        verify(running, never()).cancelJob();
    }
}
