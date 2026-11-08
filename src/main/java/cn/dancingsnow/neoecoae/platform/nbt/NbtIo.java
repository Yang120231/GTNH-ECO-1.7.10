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

    private static final int IO_BUFFER_SIZE = 64 * 1024;

    private NbtIo() {}

    public static CompoundTag read(DataInput input, NbtAccounter budget) throws IOException {
        return new CompoundTag(CompressedStreamTools.func_152456_a(input, budget.nativeTracker()));
    }

    public static void write(CompoundTag tag, DataOutput output) throws IOException {
        CompressedStreamTools.write(tag.nativeTag(), output);
    }

    public static CompoundTag readCompressed(Path path, NbtAccounter budget) throws IOException {
        // Buffer the compressed file as well as decoded NBT; buffering only above GZIP leaves tiny disk reads.
        try (var file = Files.newInputStream(path);
            DataInputStream input = new DataInputStream(
                new BufferedInputStream(
                    new GZIPInputStream(new BufferedInputStream(file, IO_BUFFER_SIZE), IO_BUFFER_SIZE)))) {
            return read(input, budget);
        }
    }

    public static void writeCompressed(CompoundTag tag, Path path) throws IOException {
        // Coalesce compressed output before it reaches the file; retain buffering of the small NBT field writes.
        try (var file = Files.newOutputStream(path);
            DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(
                    new GZIPOutputStream(new BufferedOutputStream(file, IO_BUFFER_SIZE), IO_BUFFER_SIZE)))) {
            write(tag, output);
        }
    }
}
