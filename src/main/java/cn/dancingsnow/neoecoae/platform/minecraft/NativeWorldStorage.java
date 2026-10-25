package cn.dancingsnow.neoecoae.platform.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

import cn.dancingsnow.neoecoae.NeoECOAE;
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
        ECOInfiniteStorageData data;
        if (!Files.exists(path) && index.names.contains(name)) {
            data = ECOInfiniteStorageData.createNew();
            data.markUnreadable(
                "Known domain snapshot is missing; refusing to replace it with an empty inventory: " + path);
        } else if (Files.exists(path)) {
            try {
                CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(256L * 1024 * 1024));
                data = factory.deserializer()
                    .apply(root.getCompound("data"), RegistryAccess.EMPTY);
            } catch (java.io.IOException | RuntimeException e) {
                data = factory.constructor()
                    .get();
                data.markUnreadable("Cannot read authoritative domain snapshot; original retained: " + e);
                NeoECOAE.LOG.error("Cannot load ECO domain {}", name, e);
            }
        } else {
            data = factory.constructor()
                .get();
            if (data.canWrite()) data.setDirty();
        }
        if (index.names.add(name)) index.markDirty();
        loaded.put(name, data);
        return data;
    }

    public static synchronized void save(MinecraftServer server) {
        Map<String, ECOInfiniteStorageData> loaded = OPEN.get(server);
        if (loaded == null) return;
        Path directory = root(server).resolve("data");
        loaded.forEach(
            (name, data) -> data.save(
                directory.resolve(name + ".dat")
                    .toFile(),
                RegistryAccess.EMPTY));
    }

    public static synchronized void close(MinecraftServer server) {
        save(server);
        IOUtilities.waitUntilIOWorkerComplete();
        OPEN.remove(server);
    }

    public static final class DomainIndex extends WorldSavedData {

        private final Set<String> names = new HashSet<>();

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
            ListTag list = new ListTag();
            for (String name : names) {
                CompoundTag entry = new CompoundTag();
                entry.putString("name", name);
                list.add(entry);
            }
            new CompoundTag(tag).put("domains", list);
        }
    }
}
