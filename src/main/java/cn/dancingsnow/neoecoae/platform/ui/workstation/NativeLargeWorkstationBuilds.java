package cn.dancingsnow.neoecoae.platform.ui.workstation;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.Holder;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageHostBlock;
import cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines;
import cn.dancingsnow.neoecoae.platform.ui.NativeHostSideBar;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeBuilderConfig;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeBuilderPages;
import cn.dancingsnow.neoecoae.platform.ui.multiblock.NativeBuilderSession;

/** Fixed source geometry and the shared material-conserving builder, owned by the physical workstation. */
public final class NativeLargeWorkstationBuilds {
    private NativeLargeWorkstationBuilds() {}

    public static MultiBlockDefinition definition() {
        return Definition.VALUE;
    }

    private static final class Definition {
        private static final MultiBlockDefinition VALUE = create();

        private static MultiBlockDefinition create() {
            MultiBlockDefinition.Builder builder = MultiBlockDefinition.builder(Holder.direct(WorkstationMachines.WORKSTATION));
            for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                BlockState state = x == 1 && y == 1 && z == 0 ? WorkstationMachines.WORKSTATION.defaultBlockState()
                    : z == 1 && y == 0
                        ? state(x == 0 ? WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH
                            : x == 1 ? WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_INTERFACE
                                : WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH)
                        : state(WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING);
                builder.setBlock(new BlockPos(x, y, z), state);
            }
            return builder.expandMin(1).expandMax(1).create();
        }

        private static BlockState state(Holder<Block> block) {
            return ((NativeMachineBlock) block.value()).defaultBlockState();
        }
    }

    public static void attach(ModularPanel panel, ECOLargeIntegratedWorkingStationBlockEntity host, EntityPlayer player,
        PanelSyncManager sync) {
        var builder = sync.syncedPanel("workstation.builder", true, (panelSync, handler) -> {
            NativeBuilderSession session = new NativeBuilderSession(host.getBuilderConfig(player));
            panelSync.syncValue("workstation.builder", session);
            return NativeBuilderPages.create(session, handler);
        });
        panel.child(NativeHostSideBar.left(false, NativeHostSideBar.button(48, 0,
            () -> Component.translatable("gui.neoecoae.multiblock_build.title"), builder::openPanel))
            .pos(-21, host.isFormed() ? 30 : NativeHostSideBar.height(2)));
    }

    public static final class State implements MultiBlockBuildController.Host {
        private final ECOLargeIntegratedWorkingStationBlockEntity host;
        private final MultiBlockBuildController controller = new MultiBlockBuildController(this);
        private boolean building;

        public State(ECOLargeIntegratedWorkingStationBlockEntity host) {
            this.host = host;
        }

        public void tick(WorldView level) {
            controller.tick(level);
        }

        public NativeBuilderConfig config(EntityPlayer player) {
            return new NativeBuilderConfig(host, player, this::getSelectedBuildLength, 1, 1,
                length -> controller.setBuildLength(player, length), delta -> controller.changeBuildLength(player, delta),
                this::isMirrorBuild, value -> controller.setMirrorBuild(player, value), () -> controller.autoBuild(player),
                host::isFormed, this::isBuildInProgress, controller::createLocalPreviewPlan);
        }

        @Override public WorldView getBuildLevel() { return host.getLevel(); }
        @Override public BlockPos getBuildPosition() { return host.getBlockPos(); }
        @Override public BlockState getBuildState() { return host.getBlockState(); }
        @Override public MultiBlockDefinition getBuildDefinition() { return definition(); }
        @Override public int getMinBuildLength() { return 1; }
        @Override public int getMaxBuildLength() { return 1; }
        @Override public int getSelectedBuildLength() { return 1; }
        @Override public void setSelectedBuildLength(int length) {}
        @Override public boolean isMirrorBuild() { return false; }
        @Override public void setMirrorBuild(boolean value) {}
        @Override public boolean isBuildInProgress() { return building; }
        @Override public void setBuildInProgress(boolean value) { building = value; }
        @Override public boolean isFormed() { return host.isFormed(); }
        @Override public boolean canPlayerInteract(EntityPlayer player) {
            return host.getLevel() != null && NativeStorageHostBlock.isPlayerCloseEnough(host.getLevel(), host.getBlockPos(), player);
        }
        @Override public void rebuildAfterBuild() { host.rebuildMultiblock(); }
        @Override public void buildStateChanged() { host.setChanged(); host.markForClientUpdate(); }
    }
}
