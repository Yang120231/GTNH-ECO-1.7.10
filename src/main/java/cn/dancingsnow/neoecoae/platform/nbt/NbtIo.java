package cn.dancingsnow.neoecoae.platform.nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.CompressedStreamTools;

public final class NbtIo {

    private NbtIo() {}

    public static CompoundTag read(DataInput input, NbtAccounter budget) throws IOException {
        return new CompoundTag(CompressedStreamTools.func_152456_a(input, budget.nativeTracker()));
    }

    public static void write(CompoundTag tag, DataOutput output) throws IOException {
        CompressedStreamTools.write(tag.nativeTag(), output);
    }

    public static CompoundTag readCompressed(Path path, NbtAccounter budget) throws IOException {
        try (DataInputStream input = new DataInputStream(
            new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
            return read(input, budget);
        }
    }

    public static void writeCompressed(CompoundTag tag, Path path) throws IOException {
        try (DataOutputStream output = new DataOutputStream(
            new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(path))))) {
            write(tag, output);
        }
    }
}
