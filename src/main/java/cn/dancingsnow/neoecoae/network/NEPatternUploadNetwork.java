package cn.dancingsnow.neoecoae.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTermUploadExtension;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public final class NEPatternUploadNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(NeoECOAE.MODID);
    private static boolean registered;

    private NEPatternUploadNetwork() {}

    public static void register() {
        if (registered) return;
        CHANNEL.registerMessage(PrepareHandler.class, PrepareMessage.class, 0, Side.SERVER);
        registered = true;
    }

    public static void requestPrepare() {
        CHANNEL.sendToServer(new PrepareMessage());
    }

    public static final class PrepareMessage implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {}

        @Override
        public void toBytes(ByteBuf buf) {}
    }

    public static final class PrepareHandler implements IMessageHandler<PrepareMessage, IMessage> {

        @Override
        public IMessage onMessage(PrepareMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerThreadUtil.addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerPatternTerm
                    && player.openContainer instanceof PatternTermUploadExtension) {
                    ((PatternTermUploadExtension) player.openContainer).neoecoae$encodeAndPrepareUpload();
                }
            });
            return null;
        }
    }
}
