package cn.dancingsnow.neoecoae.platform.ae2.grid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.storage.ICellContainer;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts.Role;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
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
    void nonStoragePartsAreNotCellContainers() {
        // rv3 posts a full-network cell rescan whenever an ICellContainer joins or leaves a grid.
        assertFalse(ICellContainer.class.isAssignableFrom(ECOCraftingNetworkSwitchBlockEntity.class));
        assertFalse(
            ICellContainer.class.isAssignableFrom(
                cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity.class));
        assertFalse(
            ICellContainer.class
                .isAssignableFrom(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity.class));
        assertTrue(
            ICellContainer.class
                .isAssignableFrom(cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity.class));
        assertTrue(
            ICellContainer.class
                .isAssignableFrom(cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity.class));
    }

    @Test
    void storageProvidersMustBeCellContainers() {
        var part = networkSwitch();
        assertThrows(
            IllegalStateException.class,
            () -> part.getMainNode()
                .addService(IStorageProvider.class, mounts -> {}));
    }

    @Test
    void storageProvidersStillRequestCellRescans() {
        var type = new BlockEntityType<StorageSwitch>("test_storage_switch", () -> null);
        var block = new NativeMachineBlock(type, Role.CASING, "crafting_network_switch");
        var provider = new StorageSwitch(type, block.defaultBlockState());
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

    static class StorageSwitch extends ECOCraftingNetworkSwitchBlockEntity implements ICellContainer {

        StorageSwitch(BlockEntityType<?> type, BlockState state) {
            super(type, BlockPos.ZERO, state);
        }
    }
}
