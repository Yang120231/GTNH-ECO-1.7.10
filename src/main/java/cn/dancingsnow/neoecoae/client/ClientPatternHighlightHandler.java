package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.network.NEPatternUploadNetwork;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public final class ClientPatternHighlightHandler
    implements IMessageHandler<NEPatternUploadNetwork.HighlightMessage, IMessage> {

    @Override
    public IMessage onMessage(NEPatternUploadNetwork.HighlightMessage message, MessageContext context) {
        ClientPatternHighlight.set(message.getDimension(), message.getX(), message.getY(), message.getZ());
        return null;
    }
}
