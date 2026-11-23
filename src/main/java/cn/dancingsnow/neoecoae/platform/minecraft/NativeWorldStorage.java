package cn.dancingsnow.neoecoae.platform.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.IOUtilities;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.NbtAccounter;
import cn.dancingsnow.neoecoae.platform.nbt.NbtIo;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;

/** 1.7.10 MapStorage tracks domain membership; domain snapshots retain the input's atomic-save contract. */
public final class NativeWorldStorage {

    private static final String INDEX = "neoecoae_domains";
    private static final Map<MinecraftServer, Map<String, ECOInfiniteStorageData>> OPEN = new IdentityHashMap<>();

    private NativeWorldStorage() {}

    public static MinecraftServer server(WorldServer world) {
        return MinecraftServer.getServer();
    }

    public static MinecraftServer server(net.minecraft.world.World world) {
        return world instanceof WorldServer && !world.isRemote ? MinecraftServer.getServer() : null;
    }

    public static WorldServer overworld(MinecraftServer server) {
        return server.worldServerForDimension(0);
    }

    public static Path root(MinecraftServer server) {
        WorldServer world = overworld(server);
        if (world == null) throw new IllegalStateException("Overworld is not loaded");
        return world.getSaveHandler()
            .getWorldDirectory()
            .toPath();
    }

    public static synchronized ECOInfiniteStorageData computeIfAbsent(WorldServer world,
        SavedData.Factory<ECOInfiniteStorageData> factory, String name) {
        MinecraftServer server = server(world);
        Map<String, ECOInfiniteStorageData> loaded = OPEN.computeIfAbsent(server, ignored -> new HashMap<>());
        ECOInfiniteStorageData current = loaded.get(name);
        if (current != null) return current;
        WorldServer owner = overworld(server);
        DomainIndex index = (DomainIndex) owner.mapStorage.loadData(DomainIndex.class, INDEX);
        if (index == null) {
            index = new DomainIndex(INDEX);
            owner.mapStorage.setData(INDEX, index);
        }
        Path path = root(server).resolve("data")
            .resolve(name + ".dat");
        Path indexPath = path.resolveSibling(INDEX + ".dat");
        ECOInfiniteStorageData data;
        if (!Files.exists(path) && index.names.contains(name)) {
            data = ECOInfiniteStorageData.createNew();
            data.markUnreadable(
                "Known domain snapshot is missing; refusing to replace it with an empty inventory: " + path);
        } else if (Files.exists(path)) {
            try {
                CompoundTag root = NbtIo
                    .readCompressed(path, NbtAccounter.create(ECOInfiniteStorageData.SNAPSHOT_NBT_LIMIT));
                data = factory.deserializer()
                    .apply(root.getCompound("data"), RegistryAccess.EMPTY);
            } catch (java.io.IOException | RuntimeException e) {
                data = factory.constructor()
                    .get();
                data.markUnreadable("Cannot read authoritative domain snapshot; original retained: " + e);
            }
        } else {
            data = factory.constructor()
                .get();
            if (data.canWrite()) data.setDirty();
        }
        DomainIndex storageIndex = index;
        data.bindSnapshot(path.toFile(), () -> storageIndex.publish(indexPath, name));
        if (!index.names.contains(name) && data.canWrite() && !data.isDirty()) data.setDirty();
        loaded.put(name, data);
        return data;
    }

    public static synchronized void save(MinecraftServer server) {
        Map<String, ECOInfiniteStorageData> loaded = OPEN.get(server);
        if (loaded == null) return;
        loaded.values()
            .forEach(ECOInfiniteStorageData::saveSnapshot);
    }

    public static synchronized void close(MinecraftServer server) {
        save(server);
        IOUtilities.waitUntilIOWorkerComplete();
        Map<String, ECOInfiniteStorageData> loaded = OPEN.get(server);
        if (loaded != null && loaded.values()
            .stream()
            .anyMatch(data -> data.pendingSnapshotFailure() != null)) {
            loaded.values()
                .stream()
                .filter(data -> data.pendingSnapshotFailure() != null)
                .forEach(ECOInfiniteStorageData::saveSnapshot);
            IOUtilities.waitUntilIOWorkerComplete();
        }
        OPEN.remove(server);
    }

    public static CompletableFuture<Boolean> checkpointTiles(WorldServer world, Map<TileEntity, List<String>> fields) {
        if (!(world.theChunkProviderServer.currentChunkLoader instanceof AnvilChunkLoader loader)) {
            throw new IllegalStateException("Cannot verify ECO transfer on a non-Anvil chunk loader");
        }
        Map<ChunkCoordIntPair, List<NBTTagCompound>> checkpoints = new HashMap<>();
        fields.forEach((tile, keys) -> {
            NBTTagCompound expected = new NBTTagCompound();
            tile.writeToNBT(expected);
            NBTTagCompound retained = new NBTTagCompound();
            for (String key : keys) retained.setTag(
                key,
                expected.getTag(key)
                    .copy());
            retained.setInteger("x", tile.xCoord);
            retained.setInteger("y", tile.yCoord);
            retained.setInteger("z", tile.zCoord);
            var chunk = new ChunkCoordIntPair(tile.xCoord >> 4, tile.zCoord >> 4);
            checkpoints.computeIfAbsent(chunk, ignored -> new ArrayList<>())
                .add(retained);
        });
        try {
            for (var chunk : checkpoints.keySet()) {
                if (!world.theChunkProviderServer.chunkExists(chunk.chunkXPos, chunk.chunkZPos)) {
                    return CompletableFuture.completedFuture(false);
                }
                loader.saveChunk(world, world.getChunkFromChunkCoords(chunk.chunkXPos, chunk.chunkZPos));
            }
        } catch (IOException | net.minecraft.world.MinecraftException e) {
            throw new IllegalStateException("Cannot request ECO transfer chunk save", e);
        }
        var directory = world.getChunkSaveLocation();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        IOUtilities.withIOWorker(() -> {
            try {
                for (var entry : checkpoints.entrySet()) {
                    var chunk = entry.getKey();
                    try (var input = RegionFileCache.getChunkInputStream(directory, chunk.chunkXPos, chunk.chunkZPos)) {
                        if (input == null || !containsTiles(CompressedStreamTools.read(input), entry.getValue())) {
                            result.complete(false);
                            return;
                        }
                    }
                }
                result.complete(true);
            } catch (IOException | RuntimeException e) {
                result.complete(false);
            }
        });
        return result;
    }

    private static boolean containsTiles(NBTTagCompound chunk, List<NBTTagCompound> expected) {
        var tiles = chunk.getCompoundTag("Level")
            .getTagList("TileEntities", 10);
        for (NBTTagCompound checkpoint : expected) {
            boolean found = false;
            for (int index = 0; index < tiles.tagCount(); index++) {
                var tile = tiles.getCompoundTagAt(index);
                if (checkpoint.getInteger("x") != tile.getInteger("x")
                    || checkpoint.getInteger("y") != tile.getInteger("y")
                    || checkpoint.getInteger("z") != tile.getInteger("z")) continue;
                found = checkpoint.func_150296_c()
                    .stream()
                    .allMatch(
                        key -> checkpoint.getTag((String) key)
                            .equals(tile.getTag((String) key)));
                break;
            }
            if (!found) return false;
        }
        return true;
    }

    public static final class DomainIndex extends WorldSavedData {

        private volatile Set<String> names = new HashSet<>();

        public DomainIndex(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            names.clear();
            for (Tag entry : new CompoundTag(tag).getList("domains", Tag.TAG_COMPOUND)) {
                String name = ((CompoundTag) entry).getString("name");
                if (!name.matches("neoecoae_infinite_[0-9a-f-]{36}_v3"))
                    throw new IllegalArgumentException("Invalid ECO domain index entry");
                names.add(name);
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            writeNames(new CompoundTag(tag), names);
        }

        private void publish(Path path, String name) {
            if (names.contains(name)) return;
            Set<String> published = new HashSet<>(names);
            published.add(name);
            CompoundTag root = new CompoundTag();
            CompoundTag index = new CompoundTag();
            writeNames(index, published);
            root.put("data", index);
            try {
                IOUtilities.writeNbtCompressed(root, path, ECOInfiniteStorageData.SNAPSHOT_NBT_LIMIT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            names = published;
        }

        private static void writeNames(CompoundTag tag, Set<String> names) {
            ListTag list = new ListTag();
            for (String name : names) {
                CompoundTag entry = new CompoundTag();
                entry.putString("name", name);
                list.add(entry);
            }
            tag.put("domains", list);
        }
    }
}
