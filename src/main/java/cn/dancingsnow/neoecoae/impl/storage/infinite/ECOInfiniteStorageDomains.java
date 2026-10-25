package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Looks up the storage engine of an infinite domain. Domains are world-global, so their data lives in the overworld's
 * data storage no matter which dimension the storage host stands in; the map here is only a cache of engines over that
 * data, discarded when the server stops. Loading and saving belong entirely to the vanilla
 * {@code DimensionDataStorage}.
 */
public final class ECOInfiniteStorageDomains {

    private static final Logger LOGGER = LogManager.getLogger(ECOInfiniteStorageDomains.class);
    private static final String DATA_NAME_PREFIX = "neoecoae_infinite_";

    private static final Map<MinecraftServer, Map<UUID, DomainEntry>> ENGINES = new IdentityHashMap<>();

    private ECOInfiniteStorageDomains() {}

    /**
     * Mounts one host on a domain. Every successful acquire must be paired with {@link #release}.
     */
    public static synchronized ECOInfiniteStorageEngine acquire(WorldServer level, UUID domainId) {
        MinecraftServer server = cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.server(level);
        DomainEntry entry = ENGINES.computeIfAbsent(server, ignored -> new Object2ObjectOpenHashMap<>())
            .computeIfAbsent(domainId, ignored -> new DomainEntry(create(server, domainId)));
        entry.mountCount++;
        return entry.engine;
    }

    /**
     * Releases one host mount. The engine stays cached for the rest of the server session: it is only a view over the
     * level's SavedData, which the vanilla save cycle persists whether or not any host is mounted.
     */
    public static synchronized void release(MinecraftServer server, UUID domainId) {
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        if (engines != null) {
            DomainEntry entry = engines.get(domainId);
            if (entry != null && entry.mountCount > 0) entry.mountCount--;
        }
    }

    /** The world has already been saved by vanilla shutdown; only drop the cached views. */
    public static synchronized void onServerStopped(MinecraftServer server) {
        ENGINES.remove(server);
    }

    public static synchronized com.google.gson.JsonObject diagnosticReport(MinecraftServer server) {
        com.google.gson.JsonObject report = new com.google.gson.JsonObject();
        report.addProperty(
            "generatedAt",
            java.time.Instant.now()
                .toString());
        com.google.gson.JsonArray domains = new com.google.gson.JsonArray();
        Map<UUID, DomainEntry> engines = ENGINES.get(server);
        if (engines != null) engines.forEach((id, entry) -> {
            ECOInfiniteStorageEngine engine = entry.engine;
            com.google.gson.JsonObject domain = new com.google.gson.JsonObject();
            domain.addProperty("domain", id.toString());
            domain.addProperty("mountedHosts", entry.mountCount);
            domain.addProperty(
                "status",
                engine.status()
                    .name());
            domain.addProperty("canRestore", engine.canExitOrRestore());
            if (engine instanceof SavedDataInfiniteStorageEngine saved) {
                domain.addProperty("persistence", saved.persistenceSummary());
                com.google.gson.JsonArray failures = new com.google.gson.JsonArray();
                saved.failures()
                    .forEach(value -> failures.add(new com.google.gson.JsonPrimitive(value)));
                domain.add("failures", failures);
            }
            domains.add(domain);
        });
        report.add("domains", domains);
        return report;
    }

    private static final class DomainEntry {

        private final ECOInfiniteStorageEngine engine;
        private int mountCount;

        private DomainEntry(ECOInfiniteStorageEngine engine) {
            this.engine = engine;
        }
    }

    private static ECOInfiniteStorageEngine create(MinecraftServer server, UUID domainId) {
        WorldServer overworld = cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.overworld(server);
        if (overworld == null) {
            throw new IllegalStateException("Cannot open ECO infinite storage domain " + domainId + " without a level");
        }
        String legacyName = DATA_NAME_PREFIX + domainId;
        // A separate name keeps every old .dat/.store byte intact. Once v3 exists it is authoritative;
        // never fall back to the stale legacy inventory when the newer file is damaged.
        String dataName = legacyName + "_v3";
        Path dataDir = cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.root(server)
            .resolve("data");
        Path dataFile = dataDir.resolve(dataName + ".dat");
        Path legacyFile = dataDir.resolve(legacyName + ".dat");
        ECOInfiniteStorageData data = cn.dancingsnow.neoecoae.platform.minecraft.NativeWorldStorage.computeIfAbsent(
            overworld,
            ECOInfiniteStorageData.factory(dataFile, () -> importLegacy(overworld, domainId, legacyFile)),
            dataName);
        if (data.status() != ECOInfiniteStorageData.DomainStatus.HEALTHY) {
            LOGGER.warn("ECO domain {} opened with status {}: {}", domainId, data.status(), data.failures());
        }
        return new SavedDataInfiniteStorageEngine(data);
    }

    private static ECOInfiniteStorageData importLegacy(WorldServer overworld, UUID domainId, Path legacyFile) {
        ECOInfiniteStorageData data = ECOInfiniteStorageData.createNew();
        try {
            if (!LegacyInfiniteStorageReader.exists(legacyFile)) return data;
            var recovered = LegacyInfiniteStorageReader.readAvailable(legacyFile);
            data = ECOInfiniteStorageData
                .load(recovered.data(), cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess.EMPTY);
            if (!recovered.failures()
                .isEmpty()) data.markIncompleteLegacy(recovered.failures());
            // Written as v3 on the next world save; the legacy files are never touched.
            if (data.canWrite()) data.setDirty();
        } catch (java.io.IOException | RuntimeException e) {
            data = ECOInfiniteStorageData.createNew();
            data.markUnreadable("Cannot import legacy domain snapshot; original files retained: " + e);
            LOGGER.error("Cannot import ECO domain {}; original files retained", domainId, e);
        }
        return data;
    }

}
