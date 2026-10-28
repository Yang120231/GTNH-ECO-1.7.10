package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

/** Overclock and cooling still refuse to change mid-run, but the refusal is now visible to the player. */
class CraftingModeSwitchNoticeTest {

    private static ECOCraftingSystemBlockEntity host(int running) {
        ECOCraftingSystemBlockEntity host = mock(ECOCraftingSystemBlockEntity.class, CALLS_REAL_METHODS);
        doReturn(running).when(host)
            .getRunningThreadCount();
        doNothing().when(host)
            .applyNetworkOverclocked(anyBoolean());
        doNothing().when(host)
            .applyNetworkActiveCooling(anyBoolean());
        return host;
    }

    @Test
    void runningGroupRejectsTheSwitchAndRaisesTheNotice() {
        var host = host(3);
        assertFalse(host.isModeSwitchBlockedNoticeActive());
        host.setOverclocked(true);
        assertTrue(host.isModeSwitchBlockedNoticeActive());
        verify(host, never()).applyNetworkOverclocked(anyBoolean());
        var cooling = host(1);
        cooling.setActiveCooling(true);
        assertTrue(cooling.isModeSwitchBlockedNoticeActive());
        verify(cooling, never()).applyNetworkActiveCooling(anyBoolean());
    }

    @Test
    void idleHostSwitchesWithoutANotice() {
        var host = host(0);
        host.setOverclocked(true);
        host.setActiveCooling(true);
        verify(host).applyNetworkOverclocked(true);
        verify(host).applyNetworkActiveCooling(true);
        assertFalse(host.isModeSwitchBlockedNoticeActive());
    }
}
