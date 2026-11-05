package cn.dancingsnow.neoecoae.platform.ui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CpuSelectionMode;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.registry.StorageTiers;

class NativeComputationStatusTasksTest {

    private final ECOCraftingCPULogic logic = mock(ECOCraftingCPULogic.class);
    private final Map<AEKey, BigInteger> active = new LinkedHashMap<>();
    private final Map<AEKey, BigInteger> pending = new LinkedHashMap<>();
    private final NativeComputationSession session;

    NativeComputationStatusTasksTest() {
        ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        when(cpu.isBusy()).thenReturn(true);
        when(cpu.getLogic()).thenReturn(logic);
        when(cpu.getTier()).thenReturn(StorageTiers.L4);
        when(cpu.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
        var progress = mock(ECOCraftingProgressView.class);
        when(progress.bigOrder()).thenReturn(Optional.empty());
        when(cpu.getProgressView()).thenReturn(progress);
        when(logic.getProcessingJobId()).thenReturn(UUID.randomUUID());
        when(logic.getExactActivePreview()).thenReturn(active);
        when(logic.getExactPendingPreview()).thenReturn(pending);
        when(logic.getExactStoredPreview()).thenReturn(Collections.emptyMap());
        when(logic.getProcessingProgress(any())).thenReturn(ECOProcessingProgress.EMPTY);
        var cluster = mock(NEComputationCluster.class);
        when(cluster.isActive()).thenReturn(true);
        when(cluster.getActiveCPUs()).thenReturn(Collections.singletonList(cpu));
        when(cluster.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
        var host = mock(ECOComputationSystemBlockEntity.class);
        when(host.getCluster()).thenReturn(cluster);
        when(host.getTier()).thenReturn(StorageTiers.L4);
        when(host.getCpuSelectionMode()).thenReturn(CpuSelectionMode.ANY);
        var node = mock(cn.dancingsnow.neoecoae.platform.ae2.grid.ManagedNode.class);
        when(node.isOnline()).thenReturn(true);
        when(host.getMainNode()).thenReturn(node);
        session = new NativeComputationSession(host);
    }

    private AEKey key(String name) {
        AEKey key = mock(AEKey.class);
        when(key.getDisplayName()).thenReturn(name);
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        when(key.toTagGeneric(any())).thenReturn(tag);
        return key;
    }

    @Test
    void rightColumnHasItsOwnBoundedPageAndPreservesExactAmounts() {
        BigInteger amount = BigInteger.ONE.shiftLeft(90);
        for (int i = 0; i < 40; i++) active.put(key(String.format("%02d", i)), amount);
        var selected = session.capture()
            .getCompound("selected");
        assertEquals(40, selected.getInt("taskCount"));
        assertEquals(
            9,
            selected.getList("tasks", 10)
                .size());
        assertEquals(
            27,
            selected.getList("items", 10)
                .size());
        session.apply(20, 39);
        selected = session.capture()
            .getCompound("selected");
        assertEquals(31, selected.getInt("taskOffset"));
        assertEquals(0, selected.getInt("itemOffset"));
        var task = selected.getList("tasks", 10)
            .getCompound(8);
        assertEquals("39", task.getString("name"));
        assertEquals(amount.toString(), task.getString("active"));
        assertTrue(task.getInt("controlToken") > 0);
    }

    @Test
    void admissionBlockedTasksAppearWhileOrdinaryPendingResourcesStayInTheGrid() {
        AEKey blocked = key("blocked"), ordinary = key("ordinary"), running = key("running");
        pending.put(blocked, BigInteger.TEN);
        pending.put(ordinary, BigInteger.TEN);
        active.put(running, BigInteger.ONE);
        when(logic.getProcessingProgress(blocked)).thenReturn(
            new ECOProcessingProgress(
                0,
                false,
                ECOProcessingProgress.State.WAITING,
                1,
                -1,
                false,
                Collections.emptyList()));
        var selected = session.capture()
            .getCompound("selected");
        var tasks = selected.getList("tasks", 10);
        assertEquals(3, selected.getInt("itemCount"));
        assertEquals(2, selected.getInt("taskCount"));
        assertEquals(
            Arrays.asList("blocked", "running"),
            Arrays.asList(
                tasks.getCompound(0)
                    .getString("name"),
                tasks.getCompound(1)
                    .getString("name")));
        assertEquals(
            ECOProcessingProgress.State.WAITING,
            ECOProcessingProgress.read(
                tasks.getCompound(0)
                    .getCompound("processing"))
                .state());
        active.clear();
        pending.clear();
        assertEquals(
            0,
            session.capture()
                .getCompound("selected")
                .getInt("taskCount"));
    }
}
