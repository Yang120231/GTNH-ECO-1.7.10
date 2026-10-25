package cn.dancingsnow.neoecoae.platform.network;

public interface PacketWritable {

    void writeToPacket(RegistryFriendlyByteBuf data);
}
