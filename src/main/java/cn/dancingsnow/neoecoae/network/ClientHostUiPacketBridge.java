package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.client.gui.ClientHostUiStateHandler;

final class ClientHostUiPacketBridge {

    private ClientHostUiPacketBridge() {}

    static void handle(HostUiStatePacket packet) {
        ClientHostUiStateHandler.handle(packet);
    }
}
