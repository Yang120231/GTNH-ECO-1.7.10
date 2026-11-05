package cn.dancingsnow.neoecoae.platform.ae2.crafting.nativecpu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import appeng.api.util.CraftCancelListener;
import appeng.api.util.CraftCompleteListener;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

class NativeECOCpuTrackingTest {

    private static NativeECOCpu cpu() throws Exception {
        // Exercise bridge state without initializing AE2's game-only registries in the standalone test JVM.
        var cpu = mock(NativeECOCpu.class, CALLS_REAL_METHODS);
        field(cpu, NativeECOCpu.class, "runtime", mock(NativeECOCpuRuntime.class));
        field(cpu, CraftingCPUCluster.class, "playersFollowingCurrentCraft", new ArrayList<String>());
        field(cpu, CraftingCPUCluster.class, "unreadNotifications", new HashMap<>());
        field(cpu, CraftingCPUCluster.class, "craftCompleteListeners", new ArrayList<>());
        field(cpu, CraftingCPUCluster.class, "defaultOnComplete", new ArrayList<>());
        field(cpu, CraftingCPUCluster.class, "craftCancelListeners", new ArrayList<>());
        field(cpu, CraftingCPUCluster.class, "craftUpdateListeners", new ArrayList<>());
        return cpu;
    }

    private static void field(Object object, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    @Test
    void followTargetsTheAllocatedCpuAndDoesNotToggleAnExistingSubscriptionOff() {
        NativeECOCpu placeholder = mock(NativeECOCpu.class);
        NativeECOCpuRuntime runtime = mock(NativeECOCpuRuntime.class);
        CraftingCPUCluster assigned = mock(CraftingCPUCluster.class);
        when(placeholder.runtime()).thenReturn(runtime);
        when(runtime.submittedCpu()).thenReturn(assigned);
        when(assigned.getPlayersFollowingCurrentCraft()).thenReturn(new ArrayList<>());

        NativeECOCpuSelection.followSubmitted(placeholder, "player");
        verify(assigned).togglePlayerFollowStatus("player");
        verify(placeholder, never()).togglePlayerFollowStatus(anyString());
        when(assigned.getPlayersFollowingCurrentCraft()).thenReturn(java.util.Collections.singletonList("player"));
        NativeECOCpuSelection.followSubmitted(placeholder, "player");
        verify(assigned, times(1)).togglePlayerFollowStatus("player");
    }

    @Test
    void completionUsesNativeListenersAndClearsTheFollowState() throws Exception {
        var cpu = cpu();
        CraftCompleteListener completed = mock(CraftCompleteListener.class);
        CraftCancelListener cancelled = mock(CraftCancelListener.class);
        cpu.addOnCompleteListener(completed);
        cpu.addOnCancelListener(cancelled);
        cpu.getPlayersFollowingCurrentCraft()
            .add("player");
        AEKey output = mock(AEKey.class);
        cpu.finishTracking(true, new GenericStack(output, 60), 123L);

        verify(completed).apply(null, 60L, 123L);
        verify(cancelled, never()).run();
        assertTrue(
            cpu.getPlayersFollowingCurrentCraft()
                .isEmpty());
        cpu.finishTracking(false, null, 0);
        verify(cancelled, never()).run();
    }

    @Test
    void cancellationDoesNotFireCompletionAndFollowersSurviveSaveReload() throws Exception {
        var cpu = cpu();
        cpu.getPlayersFollowingCurrentCraft()
            .add("player");
        var saved = new NBTTagCompound();
        cpu.writeTracking(saved);
        var restored = cpu();
        restored.readTracking(saved);
        assertEquals(java.util.Collections.singletonList("player"), restored.getPlayersFollowingCurrentCraft());

        CraftCompleteListener completed = mock(CraftCompleteListener.class);
        CraftCancelListener cancelled = mock(CraftCancelListener.class);
        restored.addOnCompleteListener(completed);
        restored.addOnCancelListener(cancelled);
        restored.finishTracking(false, null, 0);
        verify(cancelled).run();
        verifyNoInteractions(completed);
        assertTrue(
            restored.getPlayersFollowingCurrentCraft()
                .isEmpty());
    }
}
