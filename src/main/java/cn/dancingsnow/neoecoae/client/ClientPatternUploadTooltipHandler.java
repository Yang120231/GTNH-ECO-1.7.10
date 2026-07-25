package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.network.NEPatternUploadNetwork;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public final class ClientPatternUploadTooltipHandler
    implements IMessageHandler<NEPatternUploadNetwork.AutoTargetMessage, IMessage> {

    @Override
    public IMessage onMessage(NEPatternUploadNetwork.AutoTargetMessage message, MessageContext context) {
        if (message.getName()
            .isEmpty()) PatternUploadButton.clearAutoTarget();
        else PatternUploadButton
            .setAutoTarget(message.getName(), message.getKind(), message.getCircuit(), message.hasProgrammingCover());
        return null;
    }
}
