package cn.dancingsnow.neoecoae.platform.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.NbtAccounter;
import cn.dancingsnow.neoecoae.platform.nbt.NbtIo;
import io.netty.buffer.ByteBuf;

/** Bounded modern-style packet primitives over Forge's real Netty buffer. */
public final class RegistryFriendlyByteBuf {

    private final ByteBuf bytes;
    private final HolderLookup.Provider registries;

    public RegistryFriendlyByteBuf(ByteBuf bytes, HolderLookup.Provider registries) {
        this.bytes = bytes;
        this.registries = registries;
    }

    public HolderLookup.Provider registryAccess() {
        return registries;
    }

    public int readableBytes() {
        return bytes.readableBytes();
    }

    public boolean isReadable() {
        return bytes.isReadable();
    }

    public boolean release() {
        return bytes.release();
    }

    public RegistryFriendlyByteBuf getBytes(int index, byte[] value) {
        bytes.getBytes(index, value);
        return this;
    }

    public RegistryFriendlyByteBuf readBytes(byte[] value) {
        bytes.readBytes(value);
        return this;
    }

    public RegistryFriendlyByteBuf readBytes(byte[] value, int offset, int count) {
        bytes.readBytes(value, offset, count);
        return this;
    }

    public RegistryFriendlyByteBuf writeVarInt(int value) {
        while ((value & ~127) != 0) {
            bytes.writeByte((value & 127) | 128);
            value >>>= 7;
        }
        bytes.writeByte(value);
        return this;
    }

    public int readVarInt() {
        int result = 0;
        for (int index = 0; index < 5; index++) {
            int value = bytes.readUnsignedByte();
            if (index == 4 && (value & 240) != 0) throw new IllegalArgumentException("Oversized VarInt");
            result |= (value & 127) << (index * 7);
            if ((value & 128) == 0) return result;
        }
        throw new IllegalArgumentException("Oversized VarInt");
    }

    public RegistryFriendlyByteBuf writeVarLong(long value) {
        while ((value & ~127L) != 0) {
            bytes.writeByte(((int) value & 127) | 128);
            value >>>= 7;
        }
        bytes.writeByte((int) value);
        return this;
    }

    public long readVarLong() {
        long result = 0;
        for (int index = 0; index < 10; index++) {
            int value = bytes.readUnsignedByte();
            if (index == 9 && (value & 254) != 0) throw new IllegalArgumentException("Oversized VarLong");
            result |= (long) (value & 127) << (index * 7);
            if ((value & 128) == 0) return result;
        }
        throw new IllegalArgumentException("Oversized VarLong");
    }

    public RegistryFriendlyByteBuf writeBoolean(boolean value) {
        bytes.writeBoolean(value);
        return this;
    }

    public boolean readBoolean() {
        return bytes.readBoolean();
    }

    public RegistryFriendlyByteBuf writeByteArray(byte[] value) {
        writeVarInt(value.length);
        bytes.writeBytes(value);
        return this;
    }

    public byte[] readByteArray(int maximum) {
        int count = readVarInt();
        if (count < 0 || count > maximum || count > bytes.readableBytes())
            throw new IllegalArgumentException("Invalid packet array length: " + count);
        byte[] value = new byte[count];
        bytes.readBytes(value);
        return value;
    }

    public RegistryFriendlyByteBuf writeUtf(String value) {
        if (value.length() > 32767) throw new IllegalArgumentException("String too long");
        return writeByteArray(value.getBytes(StandardCharsets.UTF_8));
    }

    public String readUtf() {
        byte[] raw = readByteArray(32767 * 4);
        String value = new String(raw, StandardCharsets.UTF_8);
        if (value.length() > 32767) throw new IllegalArgumentException("String too long");
        return value;
    }

    public RegistryFriendlyByteBuf writeResourceLocation(ResourceLocation id) {
        return writeUtf(id.toString());
    }

    public ResourceLocation readResourceLocation() {
        return ResourceLocation.parse(readUtf());
    }

    public RegistryFriendlyByteBuf writeEnum(Enum<?> value) {
        return writeVarInt(value.ordinal());
    }

    public <T extends Enum<T>> T readEnum(Class<T> type) {
        int ordinal = readVarInt();
        T[] values = type.getEnumConstants();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Invalid enum ordinal");
        return values[ordinal];
    }

    public RegistryFriendlyByteBuf writeNbt(CompoundTag tag) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(raw)) {
                NbtIo.write(tag, output);
            }
            if (raw.size() > 4 * 1024 * 1024) throw new IllegalArgumentException("NBT packet too large");
            return writeByteArray(raw.toByteArray());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot encode NBT", e);
        }
    }

    public CompoundTag readNbt() {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(readByteArray(4 * 1024 * 1024)))) {
            return NbtIo.read(input, NbtAccounter.create(4L * 1024 * 1024));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot decode NBT", e);
        }
    }
}
