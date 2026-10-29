package cn.dancingsnow.neoecoae.platform.registry;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Top-level so StructureLib's canonical-name registry and BlockRenderer6343's Class.forName agree. */
public class Workstation extends ECOLargeIntegratedWorkingStationBlockEntity
    implements com.cleanroommc.modularui.api.IGuiHolder<com.cleanroommc.modularui.factory.PosGuiData> {

    public Workstation() {
        super(WorkstationMachines.TYPE, BlockPos.ZERO, WorkstationMachines.WORKSTATION.defaultBlockState());
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
        return panel;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public com.cleanroommc.modularui.screen.ModularScreen createScreen(
        com.cleanroommc.modularui.factory.PosGuiData data, com.cleanroommc.modularui.screen.ModularPanel panel) {
        return new com.cleanroommc.modularui.screen.ModularScreen("neoecoae", panel);
    }
}
