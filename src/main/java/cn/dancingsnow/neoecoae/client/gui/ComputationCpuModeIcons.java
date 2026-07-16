package cn.dancingsnow.neoecoae.client.gui;

import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class ComputationCpuModeIcons {

    private ComputationCpuModeIcons() {}

    static AEA2ToolbarIconButton.Sprite icon(ComputationCpuSelectionMode mode) {
        if (mode == ComputationCpuSelectionMode.PLAYER_ONLY) {
            return AEA2ToolbarIconButton.S_TERMINAL;
        }
        if (mode == ComputationCpuSelectionMode.MACHINE_ONLY) {
            return AEA2ToolbarIconButton.S_MACHINE;
        }
        return AEA2ToolbarIconButton.CRAFT_HAMMER;
    }
}
