package cn.dancingsnow.neoecoae.storage.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOCapacityPolicy;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageSnapshot;

public class ECOStorageDomainData extends WorldSavedData {

    private static final String DATA_NAME = NeoECOAE.MODID + "_storage_domains";
    private static final int DATA_VERSION = 2;

    private final Map<UUID, ECOStorageBackend> domains = new LinkedHashMap<UUID, ECOStorageBackend>();
    private final Map<UUID, Set<UUID>> committedSources = new LinkedHashMap<UUID, Set<UUID>>();
    private final Map<UUID, NBTTagCompound> restorePlans = new LinkedHashMap<>();
    private final Set<UUID> durableRestores = new HashSet<>();

    public NBTTagCompound getRestorePlan(UUID domainId) {
        NBTTagCompound plan = this.restorePlans.get(domainId);
        return plan == null ? null : (NBTTagCompound) plan.copy();
    }

    public void beginRestore(UUID domainId, NBTTagCompound plan) {
        if (!this.domains.containsKey(domainId) || this.restorePlans.containsKey(domainId)) {
            throw new IllegalStateException("Invalid storage restore state");
        }
        this.restorePlans.put(domainId, (NBTTagCompound) plan.copy());
        this.markDirty();
    }

    public void completeRestore(UUID domainId) {
        NBTTagCompound plan = this.restorePlans.get(domainId);
        if (plan == null) return;
        plan.setBoolean("Completed", true);
        ECOStorageBackend domain = this.domains.get(domainId);
        if (domain != null) domain.clear();
        this.markDirty();
    }

    /** A checked, fsynced barrier: vanilla MapStorage logs and swallows write failures. */
    public void saveDurably(World world) {
        java.io.File destination = storageWorld(world).getSaveHandler()
            .getMapFileFromName(DATA_NAME);
        if (destination == null) throw new IllegalStateException("Storage journal has no save path");
        java.nio.file.Path temporary = null;
        try {
            java.nio.file.Path target = destination.toPath();
            java.nio.file.Files.createDirectories(target.getParent());
            temporary = java.nio.file.Files.createTempFile(target.getParent(), DATA_NAME, ".pending");
            NBTTagCompound root = new NBTTagCompound();
            NBTTagCompound payload = new NBTTagCompound();
            this.writeToNBT(payload);
            root.setTag("data", payload);
            byte[] bytes = net.minecraft.nbt.CompressedStreamTools.compress(root);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(temporary.toFile())) {
                out.write(bytes);
                out.getFD()
                    .sync();
            }
            java.nio.file.Files.move(
                temporary,
                target,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            this.setDirty(false);
            for (Map.Entry<UUID, NBTTagCompound> entry : this.restorePlans.entrySet()) if (entry.getValue()
                .getBoolean("Completed")) this.durableRestores.add(entry.getKey());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot durably commit ECO storage journal", failure);
        } finally {
            if (temporary != null) {
                try {
                    java.nio.file.Files.deleteIfExists(temporary);
                } catch (java.io.IOException ignored) {}
            }
        }
    }

    /** Replay only a still-sealed member. A portable cell may already have been used. */
    public boolean recoverRestoredMember(net.minecraft.item.ItemStack stack) {
        if (!cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata.hasNonPortableState(stack)) return false;
        UUID domainId = cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata.getHostDomainId(stack);
        UUID diskId = cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata.getDiskId(stack);
        NBTTagCompound plan = this.restorePlans.get(domainId);
        if (plan == null || !this.durableRestores.contains(domainId)
            || !plan.getBoolean("Completed")
            || diskId == null
            || !plan.hasKey(diskId.toString(), 10)) return false;
        stack.getTagCompound()
            .setTag(
                "ECOStorage",
                plan.getCompoundTag(diskId.toString())
                    .copy());
        cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata.clearDomainBinding(stack);
        return true;
    }

    public ECOStorageDomainData() {
        super(DATA_NAME);
    }

    public ECOStorageDomainData(String name) {
        super(name);
    }

    public static ECOStorageDomainData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("World must not be null");
        }
        World storageWorld = storageWorld(world);
        ECOStorageDomainData data = (ECOStorageDomainData) storageWorld.perWorldStorage
            .loadData(ECOStorageDomainData.class, DATA_NAME);
        if (data == null) {
            data = new ECOStorageDomainData();
            storageWorld.perWorldStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    private static World storageWorld(World world) {
        MinecraftServer server = world instanceof WorldServer ? ((WorldServer) world).func_73046_m() : null;
        if (server != null) {
            WorldServer overworld = server.worldServerForDimension(0);
            if (overworld != null) {
                return overworld;
            }
        }
        return world;
    }

    public ECOStorageBackend getOrCreateDomain(UUID domainId) {
        ECOStorageBackend backend = this.domains.get(domainId);
        if (backend == null) {
            backend = new ECOStorageBackend(ECOCapacityPolicy.infinite());
            backend.setMutationListener(this::markDirty);
            this.domains.put(domainId, backend);
            this.markDirty();
        }
        return backend;
    }

    public ECOStorageBackend getDomain(UUID domainId) {
        return this.domains.get(domainId);
    }

    /** Returns the non-empty domain UUIDs currently indexed by this save data. */
    public List<UUID> getDomainIds() {
        List<UUID> ids = new ArrayList<UUID>(this.domains.keySet());
        Collections.sort(
            ids,
            (left, right) -> left.toString()
                .compareTo(right.toString()));
        return ids;
    }

    public int getDomainTypeCount(UUID domainId) {
        ECOStorageBackend backend = this.domains.get(domainId);
        return backend == null ? 0 : backend.getTypeCount();
    }

    public ECOAmount getDomainUsed(UUID domainId) {
        ECOStorageBackend backend = this.domains.get(domainId);
        return backend == null ? ECOAmount.ZERO : backend.getUsed();
    }

    public boolean isDomainEmpty(UUID domainId) {
        ECOStorageBackend backend = this.domains.get(domainId);
        return backend == null || backend.isEmpty();
    }

    public void removeDomain(UUID domainId) {
        // Keep completed target snapshots as recovery tombstones for stale chunks.
        NBTTagCompound restore = this.restorePlans.get(domainId);
        if (restore != null && restore.getBoolean("Completed")) return;
        if (this.restorePlans.remove(domainId) != null) this.markDirty();
        if (this.domains.remove(domainId) != null) {
            this.markDirty();
        }
        if (this.committedSources.remove(domainId) != null) {
            this.markDirty();
        }
    }

    public void forgetCommittedSource(UUID domainId, UUID diskId) {
        if (domainId == null || diskId == null) {
            return;
        }
        Set<UUID> committed = this.committedSources.get(domainId);
        if (committed == null || !committed.remove(diskId)) {
            return;
        }
        if (committed.isEmpty()) {
            this.committedSources.remove(domainId);
        }
        this.markDirty();
    }

    /**
     * Adds a physical member UUID to a domain without copying storage. This is used by recovery:
     * the domain already owns the data and a new, empty matrix set is only changing the owner link.
     */
    public void bindCommittedSource(UUID domainId, UUID diskId) {
        if (domainId == null || diskId == null || !this.domains.containsKey(domainId)) {
            return;
        }
        Set<UUID> committed = this.committedSources.get(domainId);
        if (committed == null) {
            committed = new HashSet<UUID>();
            this.committedSources.put(domainId, committed);
        }
        if (committed.add(diskId)) {
            this.markDirty();
        }
    }

    public void replaceCommittedSources(UUID domainId, Collection<UUID> diskIds) {
        if (domainId == null || !this.domains.containsKey(domainId)) {
            return;
        }
        Set<UUID> replacement = new HashSet<UUID>();
        if (diskIds != null) {
            for (UUID diskId : diskIds) {
                if (diskId != null) {
                    replacement.add(diskId);
                }
            }
        }
        if (replacement.isEmpty()) {
            this.committedSources.remove(domainId);
        } else {
            this.committedSources.put(domainId, replacement);
        }
        this.markDirty();
    }

    public void commitDiskToDomain(UUID domainId, UUID diskId, ECOStorageBackend source) {
        if (domainId == null || diskId == null || source == null) {
            return;
        }
        Set<UUID> committed = this.committedSources.get(domainId);
        if (committed == null) {
            committed = new HashSet<UUID>();
            this.committedSources.put(domainId, committed);
        }
        if (committed.contains(diskId)) {
            return;
        }
        ECOStorageBackend domain = this.getOrCreateDomain(domainId);
        if (!source.isHealthy() || !domain.isHealthy()) {
            throw new IllegalStateException("Cannot migrate an unavailable ECO storage snapshot");
        }
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : source.getEntriesView()
            .entrySet()) {
            ECOAmount inserted = domain.insert(entry.getKey(), entry.getValue(), false);
            if (!inserted.equals(entry.getValue())) {
                throw new IllegalStateException("Infinite storage domain rejected a migration entry");
            }
        }
        committed.add(diskId);
        this.markDirty();
    }

    public ECOStorageSnapshot snapshot(UUID domainId) {
        ECOStorageBackend backend = this.domains.get(domainId);
        return backend == null ? new ECOStorageBackend(ECOCapacityPolicy.infinite()).snapshot() : backend.snapshot();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        this.domains.clear();
        this.committedSources.clear();
        this.restorePlans.clear();
        this.durableRestores.clear();
        NBTTagList list = tag.getTagList("domains", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound domainTag = list.getCompoundTagAt(i);
            UUID domainId = readUuid(domainTag.getString("id"));
            if (domainId == null) {
                continue;
            }
            ECOStorageBackend backend = new ECOStorageBackend(ECOCapacityPolicy.infinite());
            try {
                backend.readFromNBT(domainTag.getCompoundTag("storage"));
            } catch (RuntimeException e) {
                backend.quarantine(domainTag.getCompoundTag("storage"), e.toString());
                NeoECOAE.LOG.error("Quarantining unreadable ECO storage domain {}: {}", domainId, e.getMessage());
            }
            backend.setMutationListener(this::markDirty);
            this.domains.put(domainId, backend);
            if (domainTag.hasKey("restorePlan")) {
                this.restorePlans.put(domainId, domainTag.getCompoundTag("restorePlan"));
                if (domainTag.getCompoundTag("restorePlan")
                    .getBoolean("Completed")) this.durableRestores.add(domainId);
            }
            NBTTagList committedTag = domainTag.getTagList("committedSources", Constants.NBT.TAG_COMPOUND);
            Set<UUID> committed = new HashSet<UUID>();
            for (int j = 0; j < committedTag.tagCount(); j++) {
                UUID diskId = readUuid(
                    committedTag.getCompoundTagAt(j)
                        .getString("diskId"));
                if (diskId != null) {
                    committed.add(diskId);
                }
            }
            if (!committed.isEmpty()) {
                this.committedSources.put(domainId, committed);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("dataVersion", DATA_VERSION);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, ECOStorageBackend> entry : this.domains.entrySet()) {
            NBTTagCompound domainTag = new NBTTagCompound();
            domainTag.setString(
                "id",
                entry.getKey()
                    .toString());
            NBTTagCompound storageTag = new NBTTagCompound();
            entry.getValue()
                .writeToNBT(storageTag);
            domainTag.setTag("storage", storageTag);
            NBTTagCompound restore = this.restorePlans.get(entry.getKey());
            if (restore != null) domainTag.setTag("restorePlan", restore.copy());
            NBTTagList committedTag = new NBTTagList();
            Set<UUID> committed = this.committedSources.get(entry.getKey());
            if (committed != null) {
                for (UUID diskId : committed) {
                    NBTTagCompound diskTag = new NBTTagCompound();
                    diskTag.setString("diskId", diskId.toString());
                    committedTag.appendTag(diskTag);
                }
            }
            domainTag.setTag("committedSources", committedTag);
            list.appendTag(domainTag);
        }
        tag.setTag("domains", list);
    }

    private static UUID readUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
