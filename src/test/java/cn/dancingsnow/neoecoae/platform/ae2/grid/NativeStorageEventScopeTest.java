package cn.dancingsnow.neoecoae.platform.ae2.grid;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts.Role;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;

/** rv3 rescans every cell provider per cell-array event, so only storage providers may request one. */
class NativeStorageEventScopeTest {

    @Test
    void nonStoragePartsDoNotRequestCellRescans() {
        var part = spy(networkSwitch());
        part.nativePowerChanged(new MENetworkPowerStatusChange());
        part.nativeChannelsChanged(new MENetworkChannelsChanged());
        verify(part, never()).requestStorageRescan(anyBoolean());
    }

    @Test
    void storageProvidersStillRequestCellRescans() {
        var provider = networkSwitch();
        provider.getMainNode()
            .addService(IStorageProvider.class, mounts -> {});
        var spied = spy(provider);
        spied.nativePowerChanged(new MENetworkPowerStatusChange());
        // Mounted cells are unchanged by power or channel state, so the rescan is merged per grid until tick end.
        verify(spied).requestStorageRescan(false);
    }

    private static ECOCraftingNetworkSwitchBlockEntity networkSwitch() {
        var type = new BlockEntityType<ECOCraftingNetworkSwitchBlockEntity>("test_switch", () -> null);
        var block = new NativeMachineBlock(type, Role.CASING, "crafting_network_switch");
        return new ECOCraftingNetworkSwitchBlockEntity(type, BlockPos.ZERO, block.defaultBlockState());
    }
}
