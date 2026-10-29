package cn.dancingsnow.neoecoae.platform.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageVentBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cpw.mods.fml.common.registry.GameRegistry;

/** Native identities/factories; every registered tile delegates behavior to the imported entities. */
public final class StorageMachines {

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final BlockEntityType<ECODriveBlockEntity> DRIVE_TYPE = new BlockEntityType<>(
        "eco_drive",
        Drive::new);
    private static final BlockEntityType<Casing> CASING_TYPE = new BlockEntityType<>("storage_casing", Casing::new);
    private static final BlockEntityType<Interface> INTERFACE_TYPE = new BlockEntityType<>(
        "storage_interface",
        Interface::new);
    private static final BlockEntityType<Vent> VENT_TYPE = new BlockEntityType<>("storage_vent", Vent::new);
    private static final BlockEntityType<HostL4> HOST_L4_TYPE = new BlockEntityType<>("storage_system_l4", HostL4::new);
    private static final BlockEntityType<HostL6> HOST_L6_TYPE = new BlockEntityType<>("storage_system_l6", HostL6::new);
    private static final BlockEntityType<HostL9> HOST_L9_TYPE = new BlockEntityType<>("storage_system_l9", HostL9::new);
    private static final BlockEntityType<EnergyL4> ENERGY_L4_TYPE = new BlockEntityType<>(
        "energy_cell_l4",
        EnergyL4::new);
    private static final BlockEntityType<EnergyL6> ENERGY_L6_TYPE = new BlockEntityType<>(
        "energy_cell_l6",
        EnergyL6::new);
    private static final BlockEntityType<EnergyL9> ENERGY_L9_TYPE = new BlockEntityType<>(
        "energy_cell_l9",
        EnergyL9::new);
    public static final Holder<Block> STORAGE_CASING = define(
        CASING_TYPE,
        new NativeCasingBlock<>(CASING_TYPE, "storage_casing"));
    public static final Holder<Block> STORAGE_INTERFACE = define(
        INTERFACE_TYPE,
        new NativeInterfaceBlock(INTERFACE_TYPE, "storage_interface"));
    public static final Holder<Block> ECO_DRIVE = define(DRIVE_TYPE, new NativeDriveBlock(DRIVE_TYPE, "eco_drive"));
    public static final Holder<Block> STORAGE_VENT = define(VENT_TYPE, new NativeVentBlock(VENT_TYPE, "storage_vent"));
    public static final Holder<Block> STORAGE_SYSTEM_L4 = define(
        HOST_L4_TYPE,
        new NativeStorageHostBlock(HOST_L4_TYPE, "storage_system_l4"));
    public static final Holder<Block> STORAGE_SYSTEM_L6 = define(
        HOST_L6_TYPE,
        new NativeStorageHostBlock(HOST_L6_TYPE, "storage_system_l6"));
    public static final Holder<Block> STORAGE_SYSTEM_L9 = define(
        HOST_L9_TYPE,
        new NativeStorageHostBlock(HOST_L9_TYPE, "storage_system_l9"));
    public static final Holder<Block> ENERGY_CELL_L4 = define(
        ENERGY_L4_TYPE,
        new NativeEnergyCellBlock(ENERGY_L4_TYPE, "energy_cell_l4"));
    public static final Holder<Block> ENERGY_CELL_L6 = define(
        ENERGY_L6_TYPE,
        new NativeEnergyCellBlock(ENERGY_L6_TYPE, "energy_cell_l6"));
    public static final Holder<Block> ENERGY_CELL_L9 = define(
        ENERGY_L9_TYPE,
        new NativeEnergyCellBlock(ENERGY_L9_TYPE, "energy_cell_l9"));

    private StorageMachines() {}

    private static Holder<Block> define(BlockEntityType<?> type, NativeMachineBlock block) {
        if (BLOCKS.put(type.id(), block) != null) throw new IllegalArgumentException("Duplicate block " + type.id());
        block.setBlockName("neoecoae." + type.id())
            .setCreativeTab(NativeContent.TAB);
        return Holder.direct(block);
    }

    public static void register() {
        BLOCKS.forEach((id, block) -> GameRegistry.registerBlock(block, NativeMachineItemBlock.class, id));
        tile(Drive.class, DRIVE_TYPE);
        tile(Casing.class, CASING_TYPE);
        tile(Interface.class, INTERFACE_TYPE);
        tile(Vent.class, VENT_TYPE);
        tile(HostL4.class, HOST_L4_TYPE);
        tile(HostL6.class, HOST_L6_TYPE);
        tile(HostL9.class, HOST_L9_TYPE);
        tile(EnergyL4.class, ENERGY_L4_TYPE);
        tile(EnergyL6.class, ENERGY_L6_TYPE);
        tile(EnergyL9.class, ENERGY_L9_TYPE);
        BetaStorageTile.register();
    }

    private static void tile(Class<? extends TileEntity> type, BlockEntityType<?> key) {
        GameRegistry.registerTileEntity(type, "neoecoae." + key.id());
    }

    public static Map<String, Block> blocks() {
        return java.util.Collections.unmodifiableMap(BLOCKS);
    }

    public static final class Drive extends ECODriveBlockEntity {

        public Drive() {
            super(DRIVE_TYPE, BlockPos.ZERO, ((NativeMachineBlock) ECO_DRIVE.value()).defaultBlockState());
        }
    }

    public static final class Casing extends ECOMachineCasingBlockEntity<NEStorageCluster> {

        public Casing() {
            super(
                CASING_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) STORAGE_CASING.value()).defaultBlockState(),
                NEStorageClusterCalculator::new);
        }
    }

    public static final class Interface extends ECOMachineInterfaceBlockEntity<NEStorageCluster>
        implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData> {

        public Interface() {
            super(
                INTERFACE_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) STORAGE_INTERFACE.value()).defaultBlockState(),
                NEStorageClusterCalculator::new);
        }

        @Override
        public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
            com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
            com.cleanroommc.modularui.screen.UISettings settings) {
            return cn.dancingsnow.neoecoae.platform.ui.storage.NativeStorageInterfacePages
                .create(this, data.getPlayer(), sync);
        }

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public com.cleanroommc.modularui.screen.ModularScreen createScreen(
            com.cleanroommc.modularui.factory.PosGuiData data, com.cleanroommc.modularui.screen.ModularPanel panel) {
            return new com.cleanroommc.modularui.screen.ModularScreen("neoecoae", panel);
        }
    }

    public static final class Vent extends ECOStorageVentBlockEntity {

        public Vent() {
            super(VENT_TYPE, BlockPos.ZERO, ((NativeMachineBlock) STORAGE_VENT.value()).defaultBlockState());
        }
    }

    public abstract static class Host extends ECOStorageSystemBlockEntity
        implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData> {

        public long nativeTickCount;

        protected Host(BlockEntityType<?> type, BlockPos pos,
            cn.dancingsnow.neoecoae.platform.minecraft.BlockState state, StorageTierDescriptor tier) {
            super(type, pos, state, tier);
        }

        @appeng.tile.TileEvent(appeng.tile.events.TileEventType.TICK)
        public void nativeServerTick() {
            if (worldObj != null && !worldObj.isRemote) {
                nativeTickCount++;
                tick(getLevel(), getBlockPos(), getBlockState());
            }
        }

        @Override
        public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
            com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
            com.cleanroommc.modularui.screen.UISettings settings) {
            return cn.dancingsnow.neoecoae.platform.ui.storage.NativeStoragePages
                .controller(this, data.getPlayer(), sync);
        }

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public com.cleanroommc.modularui.screen.ModularScreen createScreen(
            com.cleanroommc.modularui.factory.PosGuiData data, com.cleanroommc.modularui.screen.ModularPanel panel) {
            return new com.cleanroommc.modularui.screen.ModularScreen("neoecoae", panel);
        }
    }

    public static final class HostL4 extends Host {

        public HostL4() {
            super(
                HOST_L4_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) STORAGE_SYSTEM_L4.value()).defaultBlockState(),
                StorageTier.L4);
        }
    }

    public static final class HostL6 extends Host {

        public HostL6() {
            super(
                HOST_L6_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) STORAGE_SYSTEM_L6.value()).defaultBlockState(),
                StorageTier.L6);
        }
    }

    public static final class HostL9 extends Host {

        public HostL9() {
            super(
                HOST_L9_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) STORAGE_SYSTEM_L9.value()).defaultBlockState(),
                StorageTier.L9);
        }
    }

    public static final class EnergyL4 extends ECOEnergyCellBlockEntity {

        public EnergyL4() {
            super(
                ENERGY_L4_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) ENERGY_CELL_L4.value()).defaultBlockState(),
                StorageTier.L4);
        }
    }

    public static final class EnergyL6 extends ECOEnergyCellBlockEntity {

        public EnergyL6() {
            super(
                ENERGY_L6_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) ENERGY_CELL_L6.value()).defaultBlockState(),
                StorageTier.L6);
        }
    }

    public static final class EnergyL9 extends ECOEnergyCellBlockEntity {

        public EnergyL9() {
            super(
                ENERGY_L9_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) ENERGY_CELL_L9.value()).defaultBlockState(),
                StorageTier.L9);
        }
    }
}
