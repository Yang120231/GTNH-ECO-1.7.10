package cn.dancingsnow.neoecoae.platform.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.ICellContainer;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageVentBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.platform.ae2.storage.IStorageProvider;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cpw.mods.fml.common.registry.GameRegistry;

/** Native identities/factories; every registered tile delegates behavior to the imported entities. */
public final class StorageMachines {

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    static final BlockEntityType<ECODriveBlockEntity> DRIVE_TYPE = new BlockEntityType<>("eco_drive", Drive::new);
    static final BlockEntityType<Casing> CASING_TYPE = new BlockEntityType<>("storage_casing", Casing::new);
    static final BlockEntityType<Interface> INTERFACE_TYPE = new BlockEntityType<>("storage_interface", Interface::new);
    static final BlockEntityType<Vent> VENT_TYPE = new BlockEntityType<>("storage_vent", Vent::new);
    static final BlockEntityType<HostL4> HOST_L4_TYPE = new BlockEntityType<>("storage_system_l4", HostL4::new);
    static final BlockEntityType<HostL6> HOST_L6_TYPE = new BlockEntityType<>("storage_system_l6", HostL6::new);
    static final BlockEntityType<HostL9> HOST_L9_TYPE = new BlockEntityType<>("storage_system_l9", HostL9::new);
    static final BlockEntityType<EnergyL4> ENERGY_L4_TYPE = new BlockEntityType<>("energy_cell_l4", EnergyL4::new);
    static final BlockEntityType<EnergyL6> ENERGY_L6_TYPE = new BlockEntityType<>("energy_cell_l6", EnergyL6::new);
    static final BlockEntityType<EnergyL9> ENERGY_L9_TYPE = new BlockEntityType<>("energy_cell_l9", EnergyL9::new);
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
        implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData>,
        ICellContainer, IAEPowerStorage, IGridTickable {

        public Interface() {
            super(
                INTERFACE_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) STORAGE_INTERFACE.value()).defaultBlockState(),
                NEStorageClusterCalculator::new);
            if (defersGridNode()) getMainNode().addService(IStorageProvider.class, mounts -> {
                if (cluster == null || !cluster.usesCompactGrid() || cluster.isDestroyed()) return;
                cluster.getController()
                    .mountInventories(mounts);
                for (var drive : cluster.getDrives()) drive.mountInventories(mounts);
            });
        }

        @Override
        public boolean isAEPublicPowerStorage() {
            return cluster != null && cluster.usesCompactGrid()
                && !cluster.getEnergyCells()
                    .isEmpty();
        }

        @Override
        public AccessRestriction getPowerFlow() {
            return isAEPublicPowerStorage() ? AccessRestriction.READ_WRITE : AccessRestriction.NO_ACCESS;
        }

        @Override
        public double getAEMaxPower() {
            double total = 0;
            if (isAEPublicPowerStorage()) for (var cell : cluster.getEnergyCells()) total += cell.getAEMaxPower();
            return total;
        }

        @Override
        public double getAECurrentPower() {
            double total = 0;
            if (isAEPublicPowerStorage()) for (var cell : cluster.getEnergyCells()) total += cell.getAECurrentPower();
            return total;
        }

        @Override
        public double injectAEPower(double amount, Actionable mode) {
            if (isAEPublicPowerStorage()) for (var cell : cluster.getEnergyCells()) {
                if (amount <= 0) break;
                amount = cell.injectAEPower(amount, mode);
            }
            return amount;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            double requested = multiplier.multiply(amount);
            double extracted = 0;
            if (isAEPublicPowerStorage()) for (var cell : cluster.getEnergyCells()) {
                if (extracted >= requested) break;
                extracted += cell.extractAEPower(requested - extracted, mode, PowerMultiplier.ONE);
            }
            return multiplier.divide(extracted);
        }

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return isAEPublicPowerStorage() ? new TickingRequest(1, 20, false, true) : null;
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (isAEPublicPowerStorage() && !cluster.isDestroyed())
                for (var cell : cluster.getEnergyCells()) cell.tickingRequest(node, ticksSinceLastCall);
            return TickRateModulation.SLEEP;
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
