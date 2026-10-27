package cn.dancingsnow.neoecoae.platform.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;
import cpw.mods.fml.common.registry.GameRegistry;

/** Native identities backed by the source workstation host, never by a placeholder inventory. */
public final class WorkstationMachines {

    public static final Property<Boolean> WORKING = Property.bool("working");
    public static final Property<Module> MODULE = new Property<>("module", Module.class, value -> true);
    private static final BlockEntityType<Workstation> TYPE = new BlockEntityType<>(
        "integrated_working_station",
        Workstation::new);
    private static final BlockEntityType<Casing> CASING_TYPE = new BlockEntityType<>(
        "large_integrated_working_station_casing",
        Casing::new);
    private static final BlockEntityType<Interface> INTERFACE_TYPE = new BlockEntityType<>(
        "large_integrated_working_station_interface",
        Interface::new);
    private static final BlockEntityType<InputHatch> INPUT_TYPE = new BlockEntityType<>(
        "large_integrated_working_station_input_hatch",
        InputHatch::new);
    private static final BlockEntityType<OutputHatch> OUTPUT_TYPE = new BlockEntityType<>(
        "large_integrated_working_station_output_hatch",
        OutputHatch::new);
    public static final NativeMachineBlock WORKSTATION = new NativeWorkstationBlock(
        TYPE,
        ECOMultiblockParts.Role.CRAFTING_WORKER,
        "integrated_working_station");
    public static final Holder<Block> LARGE_INTEGRATED_WORKING_STATION_CASING = Holder
        .direct(new NativeCasingBlock<>(CASING_TYPE, CASING_TYPE.id()));
    public static final Holder<Block> LARGE_INTEGRATED_WORKING_STATION_INTERFACE = Holder
        .direct(new NativeWorkstationBlock(INTERFACE_TYPE, ECOMultiblockParts.Role.INTERFACE, INTERFACE_TYPE.id()));
    public static final Holder<Block> LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH = Holder
        .direct(new NativeWorkstationBlock(INPUT_TYPE, ECOMultiblockParts.Role.INTERFACE, INPUT_TYPE.id()));
    public static final Holder<Block> LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH = Holder
        .direct(new NativeWorkstationBlock(OUTPUT_TYPE, ECOMultiblockParts.Role.INTERFACE, OUTPUT_TYPE.id()));
    public static final Property<cn.dancingsnow.neoecoae.platform.minecraft.Direction> FACING = NativeMachineBlock.FACING;
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

    private WorkstationMachines() {}

    public enum Module {
        REGULAR
    }

    public static void register() {
        if (!BLOCKS.isEmpty()) throw new IllegalStateException("Workstation machines registered twice");
        register(TYPE, WORKSTATION, Workstation.class);
        register(CASING_TYPE, LARGE_INTEGRATED_WORKING_STATION_CASING.value(), Casing.class);
        register(INTERFACE_TYPE, LARGE_INTEGRATED_WORKING_STATION_INTERFACE.value(), Interface.class);
        register(INPUT_TYPE, LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH.value(), InputHatch.class);
        register(OUTPUT_TYPE, LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH.value(), OutputHatch.class);
    }

    private static void register(BlockEntityType<?> type, Block block, Class<? extends TileEntity> tile) {
        block.setBlockName("neoecoae." + type.id())
            .setCreativeTab(NativeContent.TAB);
        GameRegistry.registerBlock(block, NativeMachineItemBlock.class, type.id());
        GameRegistry.registerTileEntity(tile, "neoecoae." + type.id());
        BLOCKS.put(type.id(), block);
    }

    public static Map<String, Block> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    public static boolean owns(String id, Block block, String sourceModel) {
        if (BLOCKS.get(id) != block) return false;
        if (!(block instanceof NativeMachineBlock)) return false;
        NativeMachineBlock machine = (NativeMachineBlock) block;
        return sourceModel.equals("neoecoae:block/" + machine.model()) && machine
            .createNewTileEntity(null, 0) instanceof cn.dancingsnow.neoecoae.platform.ae2.multiblock.IAEMultiBlock;
    }

    public static class Workstation extends ECOLargeIntegratedWorkingStationBlockEntity
        implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData> {

        public Workstation() {
            super(TYPE, BlockPos.ZERO, WORKSTATION.defaultBlockState());
        }

        @Override
        public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
            com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
            com.cleanroommc.modularui.screen.UISettings settings) {
            var panel = isFormed()
                ? cn.dancingsnow.neoecoae.platform.ui.workstation.NativeLargeWorkstationPages.create(
                    new cn.dancingsnow.neoecoae.platform.ui.workstation.NativeLargeWorkstationPages.Config(
                        this,
                        this::getUiSnapshot,
                        this::returnTaskInputs,
                        this::toggleOverclocked,
                        this::toggleActiveCooling,
                        this::togglePreheating,
                        getInputTank(),
                        getOutputTank()),
                    data.getPlayer(),
                    sync)
                : cn.dancingsnow.neoecoae.platform.ui.workstation.NativeWorkstationPages
                    .create(this, data.getPlayer(), sync);
            cn.dancingsnow.neoecoae.platform.ui.workstation.NativeLargeWorkstationBuilds
                .attach(panel, this, data.getPlayer(), sync);
            return panel;
        }

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public com.cleanroommc.modularui.screen.ModularScreen createScreen(
            com.cleanroommc.modularui.factory.PosGuiData data, com.cleanroommc.modularui.screen.ModularPanel panel) {
            return new com.cleanroommc.modularui.screen.ModularScreen("neoecoae", panel);
        }
    }

    public static class Casing extends NEBlockEntity<NEIntegratedWorkingStationCluster, Casing> {

        public Casing() {
            super(
                CASING_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) LARGE_INTEGRATED_WORKING_STATION_CASING.value()).defaultBlockState(),
                NEIntegratedWorkingStationClusterCalculator::new);
        }
    }

    public static class Interface extends ECOLargeIntegratedWorkingStationInterfaceBlockEntity
        implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData> {

        public Interface() {
            super(
                INTERFACE_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) LARGE_INTEGRATED_WORKING_STATION_INTERFACE.value()).defaultBlockState(),
                NEIntegratedWorkingStationClusterCalculator::new);
        }

        @Override
        public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
            com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
            com.cleanroommc.modularui.screen.UISettings settings) {
            return cn.dancingsnow.neoecoae.platform.ui.workstation.NativeLargeWorkstationInterfacePages
                .create(this, data.getPlayer(), sync);
        }
    }

    public static class InputHatch extends ECOLargeIntegratedWorkingStationInputHatchBlockEntity
        implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData> {

        public InputHatch() {
            super(
                INPUT_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH.value()).defaultBlockState());
        }

        @Override
        public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
            com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
            com.cleanroommc.modularui.screen.UISettings settings) {
            return cn.dancingsnow.neoecoae.platform.ui.workstation.NativeLargeWorkstationHatchPages.create(this, true);
        }
    }

    public static class OutputHatch extends ECOLargeIntegratedWorkingStationOutputHatchBlockEntity
        implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData> {

        public OutputHatch() {
            super(
                OUTPUT_TYPE,
                BlockPos.ZERO,
                ((NativeMachineBlock) LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH.value()).defaultBlockState());
        }

        @Override
        public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
            com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
            com.cleanroommc.modularui.screen.UISettings settings) {
            return cn.dancingsnow.neoecoae.platform.ui.workstation.NativeLargeWorkstationHatchPages.create(this, false);
        }
    }
}
