package cn.dancingsnow.neoecoae.gui.state;

import cn.dancingsnow.neoecoae.tile.TileECOController;
import io.netty.buffer.ByteBuf;

public final class SimpleHostUiState {

    public static final SimpleHostUiState EMPTY = new SimpleHostUiState(false, false, "L4", "", 0, "");

    public final boolean formed;
    public final boolean mirrored;
    public final String tier;
    public final String subsystem;
    public final int memberCount;
    public final String formationMessage;

    public SimpleHostUiState(boolean formed, boolean mirrored, String tier, String subsystem, int memberCount,
        String formationMessage) {
        this.formed = formed;
        this.mirrored = mirrored;
        this.tier = tier == null ? "" : tier;
        this.subsystem = subsystem == null ? "" : subsystem;
        this.memberCount = Math.max(0, memberCount);
        this.formationMessage = formationMessage == null ? "" : formationMessage;
    }

    public static SimpleHostUiState create(TileECOController controller) {
        if (controller == null) {
            return EMPTY;
        }
        return new SimpleHostUiState(
            controller.isFormed(),
            controller.isMirrored(),
            controller.getTier().name(),
            controller.getSubsystem().getId(),
            controller.getFormedMemberBlocks().size(),
            controller.getLastFormationMessage());
    }

    public void write(ByteBuf buffer) {
        buffer.writeBoolean(this.formed);
        buffer.writeBoolean(this.mirrored);
        writeString(buffer, this.tier);
        writeString(buffer, this.subsystem);
        buffer.writeInt(this.memberCount);
        writeString(buffer, this.formationMessage);
    }

    public static SimpleHostUiState read(ByteBuf buffer) {
        return new SimpleHostUiState(
            buffer.readBoolean(),
            buffer.readBoolean(),
            readString(buffer),
            readString(buffer),
            buffer.readInt(),
            readString(buffer));
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 512);
        buffer.writeShort(length);
        buffer.writeBytes(bytes, 0, length);
    }

    private static String readString(ByteBuf buffer) {
        int length = Math.min(Math.max(0, buffer.readUnsignedShort()), 512);
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
