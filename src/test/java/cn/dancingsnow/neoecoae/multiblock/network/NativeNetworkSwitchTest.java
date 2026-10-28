package cn.dancingsnow.neoecoae.multiblock.network;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkEvent;
import appeng.me.Grid;
import appeng.me.GridNode;
import appeng.me.NetworkEventBus;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode;
import cn.dancingsnow.neoecoae.platform.ae2.grid.NodeListener;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerRuntime;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;

/** Exercise the pinned AE2 event bus and production regrouping, including boots with unchanged channels. */
class NativeNetworkSwitchTest {

    @AfterEach
    void clearNetworks() {
        NELogicalNetworkManager.clearAll();
    }

    @Test
    void craftingBootCompletionSharesWorkersAndRebootReconnectsWithoutChannelChanges() throws Exception {
        ServerWorldView level = level();
        ManagedNode node = node();
        List<NECraftingCluster> clusters = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            var controller = controller(ECOCraftingSystemBlockEntity.class, level, node, index);
            when(controller.hasNetworkSwitch()).thenReturn(true);
            when(controller.hasNormalNetworkSwitch()).thenReturn(true);
            when(controller.hasNetworkFrequency()).thenReturn(true);
            when(controller.getNetworkFrequency()).thenReturn(1);
            var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
            doCallRealMethod().when(controller)
                .updateCluster(cluster);
            controller.updateCluster(cluster);
            cluster.addBlockEntity(controller);
            var worker = mock(ECOCraftingWorkerBlockEntity.class);
            when(worker.getCluster()).thenReturn(cluster);
            cluster.addBlockEntity(worker);
            NELogicalNetworkManager.attach(cluster);
            clusters.add(cluster);
        }
        NELogicalNetworkManager.tick(level);
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));

        when(node.isOnline()).thenReturn(true);
        for (var cluster : clusters) post(cluster.getController(), new MENetworkBootingStatusChange(false));
        NELogicalNetworkManager.tick(level);
        var network = clusters.get(0)
            .getNetworkCluster();
        assertNotNull(network, "AE2 boot completion must join online hosts even when channel counts did not change");
        assertSame(
            network,
            clusters.get(1)
                .getNetworkCluster());
        for (var cluster : clusters) {
            assertEquals(
                2,
                cluster.collectDispatchCandidateWorkers()
                    .size());
            assertTrue(
                cluster.isDispatchCandidate(
                    clusters.get(1)
                        .getWorkers()
                        .get(0)));
        }

        when(node.isOnline()).thenReturn(false);
        for (var cluster : clusters) post(cluster.getController(), new MENetworkBootingStatusChange(true));
        NELogicalNetworkManager.tick(level);
        clusters.forEach(cluster -> {
            assertNull(cluster.getNetworkCluster());
            assertEquals(
                1,
                cluster.collectDispatchCandidateWorkers()
                    .size());
        });
        when(node.isOnline()).thenReturn(true);
        for (var cluster : clusters) post(cluster.getController(), new MENetworkBootingStatusChange(false));
        NELogicalNetworkManager.tick(level);
        assertSame(
            clusters.get(0)
                .getNetworkCluster(),
            clusters.get(1)
                .getNetworkCluster());
        assertNotNull(
            clusters.get(0)
                .getNetworkCluster());
    }

    @Test
    void computationBootCompletionPoolsBytesThreadsAndParallelism() throws Exception {
        ServerWorldView level = level();
        ManagedNode node = node();
        List<NEComputationCluster> clusters = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            var controller = controller(ECOComputationSystemBlockEntity.class, level, node, index);
            when(controller.hasNetworkSwitch()).thenReturn(true);
            when(controller.hasNormalNetworkSwitch()).thenReturn(index == 0);
            when(controller.hasHighEnergyNetworkSwitch()).thenReturn(index == 1);
            when(controller.hasNetworkFrequency()).thenReturn(true);
            when(controller.getNetworkFrequency()).thenReturn(1);
            var cluster = spy(new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO));
            doReturn(100L).when(cluster)
                .getTotalStorage();
            doReturn(4).when(cluster)
                .getOwnMaxThreads();
            doReturn(64).when(cluster)
                .getCPUAccelerators();
            doCallRealMethod().when(controller)
                .updateCluster(cluster);
            controller.updateCluster(cluster);
            cluster.addBlockEntity(controller);
            NELogicalNetworkManager.attach(cluster);
            clusters.add(cluster);
        }
        NELogicalNetworkManager.tick(level);
        when(node.isOnline()).thenReturn(true);
        for (var cluster : clusters) post(cluster.getController(), new MENetworkBootingStatusChange(false));
        NELogicalNetworkManager.tick(level);
        assertNotNull(
            clusters.get(0)
                .getNetworkCluster());
        assertSame(
            clusters.get(0)
                .getNetworkCluster(),
            clusters.get(1)
                .getNetworkCluster());
        for (var cluster : clusters) {
            assertEquals(2, cluster.getNetworkMemberCount());
            assertEquals(1000L, cluster.getPooledTotalStorage());
            assertEquals(40, cluster.getMaxThreads());
            assertEquals(640, cluster.getPooledParallelism());
        }

        IGrid nativeGrid = node.getGrid()
            .nativeGrid();
        clearInvocations(nativeGrid);
        when(
            clusters.get(0)
                .getController()
                .getNetworkFrequency()).thenReturn(2);
        NELogicalNetworkManager.refresh(clusters.get(0));
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));
        verify(nativeGrid, atLeastOnce()).postEvent(isA(MENetworkCraftingCpuChange.class));
        when(
            clusters.get(0)
                .getController()
                .getNetworkFrequency()).thenReturn(1);
        NELogicalNetworkManager.refresh(clusters.get(0));
        assertSame(
            clusters.get(0)
                .getNetworkCluster(),
            clusters.get(1)
                .getNetworkCluster());
        assertEquals(
            1,
            clusters.stream()
                .filter(NEComputationCluster::isNetworkRepresentative)
                .count());

        NELogicalNetworkManager.clear(level);
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));
        NELogicalNetworkManager.tick(level);
        clusters.forEach(cluster -> assertNull(cluster.getNetworkCluster()));
    }

    @Test
    void craftingGroupsRespectHostLimitGridFrequencyDimensionAndInstalledSwitch() {
        ServerWorldView level = level(), otherLevel = level();
        ManagedNode node = node(), otherGridNode = node();
        when(node.isOnline()).thenReturn(true);
        when(otherGridNode.isOnline()).thenReturn(true);
        List<NECraftingCluster> clusters = new ArrayList<>();
        for (int index = 0; index < 13; index++) {
            var controller = controller(
                ECOCraftingSystemBlockEntity.class,
                index == 12 ? otherLevel : level,
                index == 10 ? otherGridNode : node,
                index);
            when(controller.hasNetworkSwitch()).thenReturn(index != 11);
            when(controller.hasNormalNetworkSwitch()).thenReturn(index != 11);
            when(controller.hasNetworkFrequency()).thenReturn(true);
            when(controller.getNetworkFrequency()).thenReturn(index == 9 ? 2 : 1);
            var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
            doCallRealMethod().when(controller)
                .updateCluster(cluster);
            controller.updateCluster(cluster);
            cluster.addBlockEntity(controller);
            NELogicalNetworkManager.attach(cluster);
            clusters.add(cluster);
        }
        assertEquals(
            8,
            clusters.subList(0, 9)
                .stream()
                .filter(cluster -> cluster.getNetworkCluster() != null)
                .count());
        for (var cluster : clusters.subList(0, 9)) if (cluster.getNetworkCluster() != null) assertEquals(
            8,
            cluster.getNetworkCluster()
                .getMembers()
                .size());
        for (var cluster : clusters.subList(9, 13)) assertNull(cluster.getNetworkCluster());
    }

    @Test
    void unassignedHostJoiningAFullFrequencyIsAllocatedTheNextOne() {
        ServerWorldView level = level();
        ManagedNode node = node();
        when(node.isOnline()).thenReturn(true);
        ECOCraftingSystemBlockEntity newcomer = null;
        for (int index = 0; index < 9; index++) {
            var controller = controller(ECOCraftingSystemBlockEntity.class, level, node, index);
            when(controller.hasNetworkSwitch()).thenReturn(true);
            when(controller.hasNormalNetworkSwitch()).thenReturn(true);
            boolean assigned = index < 8;
            when(controller.hasNetworkFrequency()).thenReturn(assigned);
            when(controller.getNetworkFrequency()).thenReturn(1);
            var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
            doCallRealMethod().when(controller)
                .updateCluster(cluster);
            controller.updateCluster(cluster);
            cluster.addBlockEntity(controller);
            NELogicalNetworkManager.attach(cluster);
            if (!assigned) newcomer = controller;
        }
        verify(newcomer).assignNetworkFrequency(2);
    }

    @Test
    void overflowingFrequencyPartitionsByHostPositionRegardlessOfAttachOrder() {
        ServerWorldView level = level();
        ManagedNode node = node();
        when(node.isOnline()).thenReturn(true);
        List<NECraftingCluster> clusters = new ArrayList<>();
        // Attach in reverse position order; hash-set iteration used to decide who overflowed.
        for (int index = 9; index >= 0; index--) {
            var controller = controller(ECOCraftingSystemBlockEntity.class, level, node, index);
            when(controller.hasNetworkSwitch()).thenReturn(true);
            when(controller.hasNormalNetworkSwitch()).thenReturn(true);
            when(controller.hasNetworkFrequency()).thenReturn(true);
            when(controller.getNetworkFrequency()).thenReturn(1);
            var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
            doCallRealMethod().when(controller)
                .updateCluster(cluster);
            controller.updateCluster(cluster);
            cluster.addBlockEntity(controller);
            NELogicalNetworkManager.attach(cluster);
            clusters.add(0, cluster);
        }
        var first = clusters.get(0)
            .getNetworkCluster();
        assertNotNull(first);
        for (int index = 0; index < 8; index++) assertSame(
            first,
            clusters.get(index)
                .getNetworkCluster(),
            "The eight lowest positions form the first partition");
        var overflow = clusters.get(8)
            .getNetworkCluster();
        assertNotNull(overflow);
        assertNotSame(first, overflow);
        assertSame(
            overflow,
            clusters.get(9)
                .getNetworkCluster());
        assertTrue(first.isLeader(clusters.get(0)));
    }

    @Test
    void computationFrequencyUsesTheSameFourChannelsAsCrafting() {
        var host = mock(ECOComputationSystemBlockEntity.class, CALLS_REAL_METHODS);
        host.setNetworkFrequency(4);
        host.setNetworkFrequency(host.getNetworkFrequency() + 1);
        assertEquals(1, host.getNetworkFrequency());
        host.setNetworkFrequency(0);
        assertEquals(4, host.getNetworkFrequency());
        host.assignNetworkFrequency(2);
        assertEquals(4, host.getNetworkFrequency(), "Automatic assignment must preserve a player's frequency");
    }

    private static ServerWorldView level() {
        var level = mock(ServerWorldView.class);
        when(level.getServer()).thenReturn(mock(ServerRuntime.class));
        return level;
    }

    private static ManagedNode node() {
        var node = mock(ManagedNode.class);
        when(node.getGrid()).thenReturn(GridView.of(mock(IGrid.class)));
        return node;
    }

    private static <T extends NEBlockEntity<?, ?>> T controller(Class<T> type, ServerWorldView level, ManagedNode node,
        int index) {
        T controller = mock(type, invocation -> {
            switch (invocation.getMethod()
                .getName()) {
                case "nativeBootingChanged":
                case "onMainNodeStateChanged":
                case "onMainNodeGridChanged":
                case "isServerStopping":
                case "getCluster":
                    return invocation.callRealMethod();
                default:
                    return RETURNS_DEFAULTS.answer(invocation);
            }
        });
        when(controller.getLevel()).thenReturn(level);
        ManagedNode hostNode = mock(ManagedNode.class, invocation -> {
            if (invocation.getMethod()
                .getName()
                .equals("stateChanged")) {
                controller.onMainNodeStateChanged(invocation.getArgument(0, NodeListener.State.class));
                return null;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        when(hostNode.isOnline()).thenAnswer(invocation -> node.isOnline());
        when(hostNode.getGrid()).thenAnswer(invocation -> node.getGrid());
        when(controller.getMainNode()).thenReturn(hostNode);
        IGridNode nativeNode = mock(IGridNode.class);
        IGrid nativeGrid = node.getGrid()
            .nativeGrid();
        when(nativeNode.getGrid()).thenReturn(nativeGrid);
        when(controller.getActionableNode()).thenReturn(nativeNode);
        when(controller.getBlockPos()).thenReturn(new BlockPos(index, 0, 0));
        when(controller.getTier()).thenReturn(StorageTiers.L9);
        return controller;
    }

    private static void post(NEBlockEntity<?, ?> controller, MENetworkEvent event) throws Exception {
        // Use AE2's real subscriber discovery and delivery, including inherited native bridge methods.
        NetworkEventBus bus = new NetworkEventBus();
        Method read = NetworkEventBus.class.getDeclaredMethod("readClass", Class.class, Class.class);
        read.setAccessible(true);
        read.invoke(bus, controller.getClass(), controller.getClass());
        GridNode nativeNode = mock(
            GridNode.class,
            invocation -> invocation.getMethod()
                .getName()
                .equals("getMachineClass") ? invocation.callRealMethod() : RETURNS_DEFAULTS.answer(invocation));
        when(nativeNode.getMachine()).thenReturn(controller);
        Method post = NetworkEventBus.class
            .getDeclaredMethod("postEventTo", Grid.class, GridNode.class, MENetworkEvent.class);
        post.setAccessible(true);
        post.invoke(bus, null, nativeNode, event);
    }
}
