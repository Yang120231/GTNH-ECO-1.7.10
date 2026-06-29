package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuSelectionMode;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class ComputationCpuModeIcons {

    private static ItemStack anyIcon;
    private static ItemStack playerIcon;
    private static ItemStack machineIcon;

    private ComputationCpuModeIcons() {}

    static ItemStack icon(ComputationCpuSelectionMode mode) {
        if (mode == ComputationCpuSelectionMode.PLAYER_ONLY) {
            if (playerIcon == null) {
                playerIcon = aeTerminalIcon();
            }
            return playerIcon;
        }
        if (mode == ComputationCpuSelectionMode.MACHINE_ONLY) {
            if (machineIcon == null) {
                machineIcon = aeExportBusIcon();
            }
            return machineIcon;
        }
        if (anyIcon == null) {
            anyIcon = aeNetworkToolIcon();
        }
        return anyIcon;
    }

    private static ItemStack aeTerminalIcon() {
        try {
            return AEApi.instance()
                .parts().partTerminal.stack(1);
        } catch (RuntimeException ignored) {
            return new ItemStack(NEBlocks.computationInterface);
        }
    }

    private static ItemStack aeExportBusIcon() {
        try {
            return AEApi.instance()
                .parts().partExportBus.stack(1);
        } catch (RuntimeException ignored) {
            return new ItemStack(NEBlocks.computationTransmitter);
        }
    }

    private static ItemStack aeNetworkToolIcon() {
        try {
            return AEApi.instance()
                .items().itemNetworkTool.stack(1);
        } catch (RuntimeException ignored) {
            return new ItemStack(NEBlocks.computationSystemL4);
        }
    }
}
