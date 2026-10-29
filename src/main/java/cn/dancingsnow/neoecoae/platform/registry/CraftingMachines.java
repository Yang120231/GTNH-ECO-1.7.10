package cn.dancingsnow.neoecoae.platform.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts.Role;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationTransmitterBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingVentBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cpw.mods.fml.common.registry.GameRegistry;

/** Complete physical crafting/computation hardware factories. */
public final class CraftingMachines {

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    static final BlockEntityType<CraftingSystemL4> CRAFTING_SYSTEM_L4_TYPE = new BlockEntityType<>(
        "crafting_system_l4",
        CraftingSystemL4::new);
    public static final Holder<Block> CRAFTING_SYSTEM_L4 = define(CRAFTING_SYSTEM_L4_TYPE, null);
    static final BlockEntityType<CraftingParallelCoreL4> CRAFTING_PARALLEL_CORE_L4_TYPE = new BlockEntityType<>(
        "crafting_parallel_core_l4",
        CraftingParallelCoreL4::new);
    public static final Holder<Block> CRAFTING_PARALLEL_CORE_L4 = define(
        CRAFTING_PARALLEL_CORE_L4_TYPE,
        Role.CRAFTING_PARALLEL_CORE);
    static final BlockEntityType<CraftingSystemL6> CRAFTING_SYSTEM_L6_TYPE = new BlockEntityType<>(
        "crafting_system_l6",
        CraftingSystemL6::new);
    public static final Holder<Block> CRAFTING_SYSTEM_L6 = define(CRAFTING_SYSTEM_L6_TYPE, null);
    static final BlockEntityType<CraftingParallelCoreL6> CRAFTING_PARALLEL_CORE_L6_TYPE = new BlockEntityType<>(
        "crafting_parallel_core_l6",
        CraftingParallelCoreL6::new);
    public static final Holder<Block> CRAFTING_PARALLEL_CORE_L6 = define(
        CRAFTING_PARALLEL_CORE_L6_TYPE,
        Role.CRAFTING_PARALLEL_CORE);
    static final BlockEntityType<CraftingSystemL9> CRAFTING_SYSTEM_L9_TYPE = new BlockEntityType<>(
        "crafting_system_l9",
        CraftingSystemL9::new);
    public static final Holder<Block> CRAFTING_SYSTEM_L9 = define(CRAFTING_SYSTEM_L9_TYPE, null);
    static final BlockEntityType<CraftingParallelCoreL9> CRAFTING_PARALLEL_CORE_L9_TYPE = new BlockEntityType<>(
        "crafting_parallel_core_l9",
        CraftingParallelCoreL9::new);
    public static final Holder<Block> CRAFTING_PARALLEL_CORE_L9 = define(
        CRAFTING_PARALLEL_CORE_L9_TYPE,
        Role.CRAFTING_PARALLEL_CORE);
    static final BlockEntityType<CraftingCasing> CRAFTING_CASING_TYPE = new BlockEntityType<>(
        "crafting_casing",
        CraftingCasing::new);
    public static final Holder<Block> CRAFTING_CASING = define(CRAFTING_CASING_TYPE, Role.CASING);
    static final BlockEntityType<CraftingInterface> CRAFTING_INTERFACE_TYPE = new BlockEntityType<>(
        "crafting_interface",
        CraftingInterface::new);
    public static final Holder<Block> CRAFTING_INTERFACE = define(CRAFTING_INTERFACE_TYPE, Role.INTERFACE);
    static final BlockEntityType<CraftingNetworkSwitch> CRAFTING_NETWORK_SWITCH_TYPE = new BlockEntityType<>(
        "crafting_network_switch",
        CraftingNetworkSwitch::new);
    public static final Holder<Block> CRAFTING_NETWORK_SWITCH = define(CRAFTING_NETWORK_SWITCH_TYPE, Role.CASING);
    static final BlockEntityType<CraftingHighEnergyNetworkSwitch> CRAFTING_HIGH_ENERGY_NETWORK_SWITCH_TYPE = new BlockEntityType<>(
        "crafting_high_energy_network_switch",
        CraftingHighEnergyNetworkSwitch::new);
    public static final Holder<Block> CRAFTING_HIGH_ENERGY_NETWORK_SWITCH = define(
        CRAFTING_HIGH_ENERGY_NETWORK_SWITCH_TYPE,
        Role.CASING);
    static final BlockEntityType<ComputationSystemL4> COMPUTATION_SYSTEM_L4_TYPE = new BlockEntityType<>(
        "computation_system_l4",
        ComputationSystemL4::new);
    public static final Holder<Block> COMPUTATION_SYSTEM_L4 = define(COMPUTATION_SYSTEM_L4_TYPE, null);
    static final BlockEntityType<ComputationParallelCoreL4> COMPUTATION_PARALLEL_CORE_L4_TYPE = new BlockEntityType<>(
        "computation_parallel_core_l4",
        ComputationParallelCoreL4::new);
    public static final Holder<Block> COMPUTATION_PARALLEL_CORE_L4 = define(
        COMPUTATION_PARALLEL_CORE_L4_TYPE,
        Role.COMPUTATION_PARALLEL_CORE);
    static final BlockEntityType<ComputationThreadingCoreL4> COMPUTATION_THREADING_CORE_L4_TYPE = new BlockEntityType<>(
        "computation_threading_core_l4",
        ComputationThreadingCoreL4::new);
    public static final Holder<Block> COMPUTATION_THREADING_CORE_L4 = define(
        COMPUTATION_THREADING_CORE_L4_TYPE,
        Role.COMPUTATION_THREADING_CORE);
    static final BlockEntityType<ComputationCoolingControllerL4> COMPUTATION_COOLING_CONTROLLER_L4_TYPE = new BlockEntityType<>(
        "computation_cooling_controller_l4",
        ComputationCoolingControllerL4::new);
    public static final Holder<Block> COMPUTATION_COOLING_CONTROLLER_L4 = define(
        COMPUTATION_COOLING_CONTROLLER_L4_TYPE,
        Role.COOLING_CONTROLLER);
    static final BlockEntityType<ComputationSystemL6> COMPUTATION_SYSTEM_L6_TYPE = new BlockEntityType<>(
        "computation_system_l6",
        ComputationSystemL6::new);
    public static final Holder<Block> COMPUTATION_SYSTEM_L6 = define(COMPUTATION_SYSTEM_L6_TYPE, null);
    static final BlockEntityType<ComputationParallelCoreL6> COMPUTATION_PARALLEL_CORE_L6_TYPE = new BlockEntityType<>(
        "computation_parallel_core_l6",
        ComputationParallelCoreL6::new);
    public static final Holder<Block> COMPUTATION_PARALLEL_CORE_L6 = define(
        COMPUTATION_PARALLEL_CORE_L6_TYPE,
        Role.COMPUTATION_PARALLEL_CORE);
    static final BlockEntityType<ComputationThreadingCoreL6> COMPUTATION_THREADING_CORE_L6_TYPE = new BlockEntityType<>(
        "computation_threading_core_l6",
        ComputationThreadingCoreL6::new);
    public static final Holder<Block> COMPUTATION_THREADING_CORE_L6 = define(
        COMPUTATION_THREADING_CORE_L6_TYPE,
        Role.COMPUTATION_THREADING_CORE);
    static final BlockEntityType<ComputationCoolingControllerL6> COMPUTATION_COOLING_CONTROLLER_L6_TYPE = new BlockEntityType<>(
        "computation_cooling_controller_l6",
        ComputationCoolingControllerL6::new);
    public static final Holder<Block> COMPUTATION_COOLING_CONTROLLER_L6 = define(
        COMPUTATION_COOLING_CONTROLLER_L6_TYPE,
        Role.COOLING_CONTROLLER);
    static final BlockEntityType<ComputationSystemL9> COMPUTATION_SYSTEM_L9_TYPE = new BlockEntityType<>(
        "computation_system_l9",
        ComputationSystemL9::new);
    public static final Holder<Block> COMPUTATION_SYSTEM_L9 = define(COMPUTATION_SYSTEM_L9_TYPE, null);
    static final BlockEntityType<ComputationParallelCoreL9> COMPUTATION_PARALLEL_CORE_L9_TYPE = new BlockEntityType<>(
        "computation_parallel_core_l9",
        ComputationParallelCoreL9::new);
    public static final Holder<Block> COMPUTATION_PARALLEL_CORE_L9 = define(
        COMPUTATION_PARALLEL_CORE_L9_TYPE,
        Role.COMPUTATION_PARALLEL_CORE);
    static final BlockEntityType<ComputationThreadingCoreL9> COMPUTATION_THREADING_CORE_L9_TYPE = new BlockEntityType<>(
        "computation_threading_core_l9",
        ComputationThreadingCoreL9::new);
    public static final Holder<Block> COMPUTATION_THREADING_CORE_L9 = define(
        COMPUTATION_THREADING_CORE_L9_TYPE,
        Role.COMPUTATION_THREADING_CORE);
    static final BlockEntityType<ComputationCoolingControllerL9> COMPUTATION_COOLING_CONTROLLER_L9_TYPE = new BlockEntityType<>(
        "computation_cooling_controller_l9",
        ComputationCoolingControllerL9::new);
    public static final Holder<Block> COMPUTATION_COOLING_CONTROLLER_L9 = define(
        COMPUTATION_COOLING_CONTROLLER_L9_TYPE,
        Role.COOLING_CONTROLLER);
    static final BlockEntityType<ComputationCasing> COMPUTATION_CASING_TYPE = new BlockEntityType<>(
        "computation_casing",
        ComputationCasing::new);
    public static final Holder<Block> COMPUTATION_CASING = define(COMPUTATION_CASING_TYPE, Role.CASING);
    static final BlockEntityType<ComputationInterface> COMPUTATION_INTERFACE_TYPE = new BlockEntityType<>(
        "computation_interface",
        ComputationInterface::new);
    public static final Holder<Block> COMPUTATION_INTERFACE = define(COMPUTATION_INTERFACE_TYPE, Role.INTERFACE);
    static final BlockEntityType<ComputationNetworkSwitch> COMPUTATION_NETWORK_SWITCH_TYPE = new BlockEntityType<>(
        "computation_network_switch",
        ComputationNetworkSwitch::new);
    public static final Holder<Block> COMPUTATION_NETWORK_SWITCH = define(COMPUTATION_NETWORK_SWITCH_TYPE, Role.CASING);
    static final BlockEntityType<ComputationHighEnergyNetworkSwitch> COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH_TYPE = new BlockEntityType<>(
        "computation_high_energy_network_switch",
        ComputationHighEnergyNetworkSwitch::new);
    public static final Holder<Block> COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH = define(
        COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH_TYPE,
        Role.CASING);
    static final BlockEntityType<CraftingWorker> CRAFTING_WORKER_TYPE = new BlockEntityType<>(
        "crafting_worker",
        CraftingWorker::new);
    public static final Holder<Block> CRAFTING_WORKER = define(CRAFTING_WORKER_TYPE, Role.CRAFTING_WORKER);
    static final BlockEntityType<FxMonitorCore> FX_MONITOR_CORE_TYPE = new BlockEntityType<>(
        "fx_monitor_core",
        FxMonitorCore::new);
    public static final Holder<Block> FX_MONITOR_CORE = define(FX_MONITOR_CORE_TYPE, Role.CRAFTING_WORKER);
    static final BlockEntityType<CraftingPatternBus> CRAFTING_PATTERN_BUS_TYPE = new BlockEntityType<>(
        "crafting_pattern_bus",
        CraftingPatternBus::new);
    public static final Holder<Block> CRAFTING_PATTERN_BUS = define(CRAFTING_PATTERN_BUS_TYPE, Role.PATTERN_STORAGE);
    static final BlockEntityType<CraftingVent> CRAFTING_VENT_TYPE = new BlockEntityType<>(
        "crafting_vent",
        CraftingVent::new);
    public static final Holder<Block> CRAFTING_VENT = define(CRAFTING_VENT_TYPE, Role.VENT);
    static final BlockEntityType<InputHatch> INPUT_HATCH_TYPE = new BlockEntityType<>("input_hatch", InputHatch::new);
    public static final Holder<Block> INPUT_HATCH = define(INPUT_HATCH_TYPE, Role.INTERFACE);
    static final BlockEntityType<OutputHatch> OUTPUT_HATCH_TYPE = new BlockEntityType<>(
        "output_hatch",
        OutputHatch::new);
    public static final Holder<Block> OUTPUT_HATCH = define(OUTPUT_HATCH_TYPE, Role.INTERFACE);
    static final BlockEntityType<ComputationDrive> COMPUTATION_DRIVE_TYPE = new BlockEntityType<>(
        "computation_drive",
        ComputationDrive::new);
    public static final Holder<Block> COMPUTATION_DRIVE = define(COMPUTATION_DRIVE_TYPE, Role.COMPUTATION_DRIVE);
    static final BlockEntityType<ComputationTransmitter> COMPUTATION_TRANSMITTER_TYPE = new BlockEntityType<>(
        "computation_transmitter",
        ComputationTransmitter::new);
    public static final Holder<Block> COMPUTATION_TRANSMITTER = define(COMPUTATION_TRANSMITTER_TYPE, Role.TRANSMITTER);

    private static Holder<Block> define(BlockEntityType<?> type, Role role) {
        NativeMachineBlock block = new NativeExecutionMachineBlock(type, role, type.id());
        block.setBlockName("neoecoae." + type.id())
            .setCreativeTab(NativeContent.TAB);
        BLOCKS.put(type.id(), block);
        return Holder.direct(block);
    }

    public static Map<String, Block> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    public static void register() {
        BLOCKS.forEach((id, block) -> GameRegistry.registerBlock(block, NativeMachineItemBlock.class, id));
        GameRegistry.registerTileEntity(CraftingSystemL4.class, "neoecoae.crafting_system_l4");
        GameRegistry.registerTileEntity(CraftingParallelCoreL4.class, "neoecoae.crafting_parallel_core_l4");
        GameRegistry.registerTileEntity(CraftingSystemL6.class, "neoecoae.crafting_system_l6");
        GameRegistry.registerTileEntity(CraftingParallelCoreL6.class, "neoecoae.crafting_parallel_core_l6");
        GameRegistry.registerTileEntity(CraftingSystemL9.class, "neoecoae.crafting_system_l9");
        GameRegistry.registerTileEntity(CraftingParallelCoreL9.class, "neoecoae.crafting_parallel_core_l9");
        GameRegistry.registerTileEntity(CraftingCasing.class, "neoecoae.crafting_casing");
        GameRegistry.registerTileEntity(CraftingInterface.class, "neoecoae.crafting_interface");
        GameRegistry.registerTileEntity(CraftingNetworkSwitch.class, "neoecoae.crafting_network_switch");
        GameRegistry
            .registerTileEntity(CraftingHighEnergyNetworkSwitch.class, "neoecoae.crafting_high_energy_network_switch");
        GameRegistry.registerTileEntity(ComputationSystemL4.class, "neoecoae.computation_system_l4");
        GameRegistry.registerTileEntity(ComputationParallelCoreL4.class, "neoecoae.computation_parallel_core_l4");
        GameRegistry.registerTileEntity(ComputationThreadingCoreL4.class, "neoecoae.computation_threading_core_l4");
        GameRegistry
            .registerTileEntity(ComputationCoolingControllerL4.class, "neoecoae.computation_cooling_controller_l4");
        GameRegistry.registerTileEntity(ComputationSystemL6.class, "neoecoae.computation_system_l6");
        GameRegistry.registerTileEntity(ComputationParallelCoreL6.class, "neoecoae.computation_parallel_core_l6");
        GameRegistry.registerTileEntity(ComputationThreadingCoreL6.class, "neoecoae.computation_threading_core_l6");
        GameRegistry
            .registerTileEntity(ComputationCoolingControllerL6.class, "neoecoae.computation_cooling_controller_l6");
        GameRegistry.registerTileEntity(ComputationSystemL9.class, "neoecoae.computation_system_l9");
        GameRegistry.registerTileEntity(ComputationParallelCoreL9.class, "neoecoae.computation_parallel_core_l9");
        GameRegistry.registerTileEntity(ComputationThreadingCoreL9.class, "neoecoae.computation_threading_core_l9");
        GameRegistry
            .registerTileEntity(ComputationCoolingControllerL9.class, "neoecoae.computation_cooling_controller_l9");
        GameRegistry.registerTileEntity(ComputationCasing.class, "neoecoae.computation_casing");
        GameRegistry.registerTileEntity(ComputationInterface.class, "neoecoae.computation_interface");
        GameRegistry.registerTileEntity(ComputationNetworkSwitch.class, "neoecoae.computation_network_switch");
        GameRegistry.registerTileEntity(
            ComputationHighEnergyNetworkSwitch.class,
            "neoecoae.computation_high_energy_network_switch");
        GameRegistry.registerTileEntity(CraftingWorker.class, "neoecoae.crafting_worker");
        GameRegistry.registerTileEntity(FxMonitorCore.class, "neoecoae.fx_monitor_core");
        // 7.3.0-beta saved its pattern bus as "neoecoae:crafting_pattern_bus"; without the alias FML drops the
        // patterns.
        GameRegistry.registerTileEntityWithAlternatives(
            CraftingPatternBus.class,
            "neoecoae.crafting_pattern_bus",
            "neoecoae:crafting_pattern_bus");
        GameRegistry.registerTileEntity(CraftingVent.class, "neoecoae.crafting_vent");
        GameRegistry.registerTileEntity(InputHatch.class, "neoecoae.input_hatch");
        GameRegistry.registerTileEntity(OutputHatch.class, "neoecoae.output_hatch");
        // 7.3.0-beta saved computation drives as "neoecoae:computation_drive"; the alias keeps their cells.
        GameRegistry.registerTileEntityWithAlternatives(
            ComputationDrive.class,
            "neoecoae.computation_drive",
            "neoecoae:computation_drive");
        GameRegistry.registerTileEntity(ComputationTransmitter.class, "neoecoae.computation_transmitter");
    }

    public static final class CraftingParallelCoreL4 extends ECOCraftingParallelCoreBlockEntity {

        public CraftingParallelCoreL4() {
            super(
                CRAFTING_PARALLEL_CORE_L4_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_PARALLEL_CORE_L4.value()).defaultBlockState(),
                StorageTiers.L4);
        }
    }

    public static final class CraftingParallelCoreL6 extends ECOCraftingParallelCoreBlockEntity {

        public CraftingParallelCoreL6() {
            super(
                CRAFTING_PARALLEL_CORE_L6_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_PARALLEL_CORE_L6.value()).defaultBlockState(),
                StorageTiers.L6);
        }
    }

    public static final class CraftingParallelCoreL9 extends ECOCraftingParallelCoreBlockEntity {

        public CraftingParallelCoreL9() {
            super(
                CRAFTING_PARALLEL_CORE_L9_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_PARALLEL_CORE_L9.value()).defaultBlockState(),
                StorageTiers.L9);
        }
    }

    public static final class CraftingCasing extends ECOMachineCasingBlockEntity<NECraftingCluster> {

        public CraftingCasing() {
            super(
                CRAFTING_CASING_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_CASING.value()).defaultBlockState(),
                NECraftingClusterCalculator::new);
        }
    }

    public static final class CraftingInterface extends ECOMachineInterfaceBlockEntity<NECraftingCluster>
        implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

        public CraftingInterface() {
            super(
                CRAFTING_INTERFACE_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_INTERFACE.value()).defaultBlockState(),
                NECraftingClusterCalculator::new);
        }
    }

    public static final class CraftingNetworkSwitch extends ECOCraftingNetworkSwitchBlockEntity {

        public CraftingNetworkSwitch() {
            super(
                CRAFTING_NETWORK_SWITCH_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_NETWORK_SWITCH.value()).defaultBlockState());
        }
    }

    public static final class CraftingHighEnergyNetworkSwitch extends ECOCraftingNetworkSwitchBlockEntity {

        public CraftingHighEnergyNetworkSwitch() {
            super(
                CRAFTING_HIGH_ENERGY_NETWORK_SWITCH_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_HIGH_ENERGY_NETWORK_SWITCH.value()).defaultBlockState());
        }
    }

    public static final class ComputationParallelCoreL4 extends ECOComputationParallelCoreBlockEntity {

        public ComputationParallelCoreL4() {
            super(
                COMPUTATION_PARALLEL_CORE_L4_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_PARALLEL_CORE_L4.value()).defaultBlockState(),
                StorageTiers.L4);
        }
    }

    public static final class ComputationThreadingCoreL4 extends ECOComputationThreadingCoreBlockEntity {

        public ComputationThreadingCoreL4() {
            super(
                COMPUTATION_THREADING_CORE_L4_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_THREADING_CORE_L4.value()).defaultBlockState(),
                StorageTiers.L4);
        }
    }

    public static final class ComputationCoolingControllerL4 extends ECOComputationCoolingControllerBlockEntity {

        public ComputationCoolingControllerL4() {
            super(
                COMPUTATION_COOLING_CONTROLLER_L4_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_COOLING_CONTROLLER_L4.value()).defaultBlockState(),
                StorageTiers.L4);
        }
    }

    public static final class ComputationParallelCoreL6 extends ECOComputationParallelCoreBlockEntity {

        public ComputationParallelCoreL6() {
            super(
                COMPUTATION_PARALLEL_CORE_L6_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_PARALLEL_CORE_L6.value()).defaultBlockState(),
                StorageTiers.L6);
        }
    }

    public static final class ComputationThreadingCoreL6 extends ECOComputationThreadingCoreBlockEntity {

        public ComputationThreadingCoreL6() {
            super(
                COMPUTATION_THREADING_CORE_L6_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_THREADING_CORE_L6.value()).defaultBlockState(),
                StorageTiers.L6);
        }
    }

    public static final class ComputationCoolingControllerL6 extends ECOComputationCoolingControllerBlockEntity {

        public ComputationCoolingControllerL6() {
            super(
                COMPUTATION_COOLING_CONTROLLER_L6_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_COOLING_CONTROLLER_L6.value()).defaultBlockState(),
                StorageTiers.L6);
        }
    }

    public static final class ComputationParallelCoreL9 extends ECOComputationParallelCoreBlockEntity {

        public ComputationParallelCoreL9() {
            super(
                COMPUTATION_PARALLEL_CORE_L9_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_PARALLEL_CORE_L9.value()).defaultBlockState(),
                StorageTiers.L9);
        }
    }

    public static final class ComputationThreadingCoreL9 extends ECOComputationThreadingCoreBlockEntity {

        public ComputationThreadingCoreL9() {
            super(
                COMPUTATION_THREADING_CORE_L9_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_THREADING_CORE_L9.value()).defaultBlockState(),
                StorageTiers.L9);
        }
    }

    public static final class ComputationCoolingControllerL9 extends ECOComputationCoolingControllerBlockEntity {

        public ComputationCoolingControllerL9() {
            super(
                COMPUTATION_COOLING_CONTROLLER_L9_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_COOLING_CONTROLLER_L9.value()).defaultBlockState(),
                StorageTiers.L9);
        }
    }

    public static final class ComputationCasing extends ECOMachineCasingBlockEntity<NEComputationCluster> {

        public ComputationCasing() {
            super(
                COMPUTATION_CASING_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_CASING.value()).defaultBlockState(),
                NEComputationClusterCalculator::new);
        }
    }

    public static final class ComputationInterface extends ECOMachineInterfaceBlockEntity<NEComputationCluster>
        implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

        public ComputationInterface() {
            super(
                COMPUTATION_INTERFACE_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_INTERFACE.value()).defaultBlockState(),
                NEComputationClusterCalculator::new);
        }
    }

    public static final class ComputationNetworkSwitch extends ECOComputationNetworkSwitchBlockEntity {

        public ComputationNetworkSwitch() {
            super(
                COMPUTATION_NETWORK_SWITCH_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_NETWORK_SWITCH.value()).defaultBlockState());
        }
    }

    public static final class ComputationHighEnergyNetworkSwitch extends ECOComputationNetworkSwitchBlockEntity {

        public ComputationHighEnergyNetworkSwitch() {
            super(
                COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH.value()).defaultBlockState());
        }
    }

    public static final class CraftingWorker extends ECOCraftingWorkerBlockEntity {

        public CraftingWorker() {
            super(
                CRAFTING_WORKER_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_WORKER.value()).defaultBlockState());
        }
    }

    public static final class FxMonitorCore extends ECOCraftingWorkerBlockEntity {

        public FxMonitorCore() {
            super(
                FX_MONITOR_CORE_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) FX_MONITOR_CORE.value()).defaultBlockState());
        }
    }

    public static final class CraftingPatternBus extends ECOCraftingPatternBusBlockEntity
        implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

        public CraftingPatternBus() {
            super(
                CRAFTING_PATTERN_BUS_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) CRAFTING_PATTERN_BUS.value()).defaultBlockState());
        }
    }

    public static final class CraftingVent extends ECOCraftingVentBlockEntity {

        public CraftingVent() {
            super(CRAFTING_VENT_TYPE, BlockPos.ZERO, ((NativeMachineBlock) CRAFTING_VENT.value()).defaultBlockState());
        }
    }

    public static final class InputHatch extends ECOFluidInputHatchBlockEntity
        implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

        public InputHatch() {
            super(INPUT_HATCH_TYPE, BlockPos.ZERO, ((NativeMachineBlock) INPUT_HATCH.value()).defaultBlockState());
        }
    }

    public static final class OutputHatch extends ECOFluidOutputHatchBlockEntity
        implements cn.dancingsnow.neoecoae.platform.ui.NativeExecutionGui {

        public OutputHatch() {
            super(OUTPUT_HATCH_TYPE, BlockPos.ZERO, ((NativeMachineBlock) OUTPUT_HATCH.value()).defaultBlockState());
        }
    }

    public static final class ComputationDrive extends ECOComputationDriveBlockEntity {

        public ComputationDrive() {
            super(
                COMPUTATION_DRIVE_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_DRIVE.value()).defaultBlockState());
        }
    }

    public static final class ComputationTransmitter extends ECOComputationTransmitterBlockEntity {

        public ComputationTransmitter() {
            super(
                COMPUTATION_TRANSMITTER_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) COMPUTATION_TRANSMITTER.value()).defaultBlockState());
        }
    }
}
