package cn.dancingsnow.neoecoae.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;

import cn.dancingsnow.neoecoae.gui.HostUiStateContainer;
import cn.dancingsnow.neoecoae.network.HostUiStatePacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientHostUiStateHandler {

    private ClientHostUiStateHandler() {}

    public static void handle(final HostUiStatePacket packet) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (minecraft.thePlayer == null) {
                    return;
                }
                Container open = minecraft.thePlayer.openContainer;
                if (open instanceof HostUiStateContainer && open.windowId == packet.windowId()) {
                    ((HostUiStateContainer) open).applyHostUiState(packet.revision(), packet.payload());
                }
            }
        });
    }
}
