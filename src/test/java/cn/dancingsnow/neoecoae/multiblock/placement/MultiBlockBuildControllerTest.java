package cn.dancingsnow.neoecoae.multiblock.placement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;

class MultiBlockBuildControllerTest {

    private final MultiBlockBuildController.Host host = mock(MultiBlockBuildController.Host.class);
    private final EntityPlayerMP player = mock(EntityPlayerMP.class);
    private final AtomicInteger length = new AtomicInteger(5);
    private final MultiBlockBuildController controller = new MultiBlockBuildController(host);

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @BeforeEach
    void setup() {
        when(host.canPlayerInteract(player)).thenReturn(true);
        when(host.getMinBuildLength()).thenReturn(1);
        when(host.getMaxBuildLength()).thenReturn(64);
        when(host.getSelectedBuildLength()).thenAnswer(invocation -> length.get());
        doAnswer(invocation -> {
            length.set(invocation.getArgument(0));
            return null;
        }).when(host)
            .setSelectedBuildLength(anyInt());
    }

    @Test
    void typedLengthThenStepUsesTheEditedValue() {
        controller.setBuildLength(player, 20);
        controller.changeBuildLength(player, 10);
        assertEquals(30, length.get());
        controller.changeBuildLength(player, -10);
        assertEquals(20, length.get());
        controller.increaseBuildLength(player);
        controller.decreaseBuildLength(player);
        assertEquals(20, length.get());
    }

    @Test
    void typedAndRelativeLengthsClampWithoutOverflow() {
        controller.setBuildLength(player, Integer.MAX_VALUE);
        assertEquals(64, length.get());
        controller.changeBuildLength(player, Integer.MAX_VALUE);
        assertEquals(64, length.get());
        controller.setBuildLength(player, Integer.MIN_VALUE);
        assertEquals(1, length.get());
        controller.changeBuildLength(player, -10);
        assertEquals(1, length.get());
        verify(host, times(2)).buildStateChanged();
    }

    @Test
    void runningBuildAndDeniedAccessRejectEdits() {
        when(host.isBuildInProgress()).thenReturn(true);
        controller.setBuildLength(player, 20);
        controller.changeBuildLength(player, 10);
        when(host.isBuildInProgress()).thenReturn(false);
        when(host.canPlayerInteract(player)).thenReturn(false);
        controller.setBuildLength(player, 20);
        controller.changeBuildLength(player, -10);
        assertEquals(5, length.get());
        verify(host, never()).setSelectedBuildLength(anyInt());
        verify(host, never()).buildStateChanged();
    }
}
