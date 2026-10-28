package cn.dancingsnow.neoecoae.platform.registry;

import static org.mockito.Mockito.*;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts.Role;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;

/** Swapping a network switch must reform the host like the source does, not dismantle its running jobs. */
class NativeSwitchRemovalTest {

    private final NativeMachineBlock block = new NativeMachineBlock(
        new BlockEntityType<>("test_switch", () -> null),
        Role.CASING,
        "crafting_network_switch");

    @Test
    void removingANetworkSwitchKeepsRunningJobs() {
        var crafting = mock(ECOCraftingNetworkSwitchBlockEntity.class);
        var computation = mock(ECOComputationNetworkSwitchBlockEntity.class);
        block.breakBlock(world(crafting), 0, 0, 0, null, 0);
        block.breakBlock(world(computation), 0, 0, 0, null, 0);
        verify(crafting, never()).breakCluster();
        verify(computation, never()).breakCluster();
    }

    @Test
    void removingAStructuralPartStillBreaksTheCluster() {
        var worker = mock(ECOCraftingWorkerBlockEntity.class);
        block.breakBlock(world(worker), 0, 0, 0, null, 0);
        verify(worker).breakCluster();
    }

    private static World world(net.minecraft.tileentity.TileEntity tile) {
        World world = mock(World.class);
        when(world.getTileEntity(0, 0, 0)).thenReturn(tile);
        return world;
    }
}
