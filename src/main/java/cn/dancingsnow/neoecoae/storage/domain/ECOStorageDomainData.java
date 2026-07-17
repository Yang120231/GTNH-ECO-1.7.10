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
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.storage.core.ECOAmount;
import cn.dancingsnow.neoecoae.storage.core.ECOCapacityPolicy;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageKey;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageSnapshot;

public class ECOStorageDomainData extends WorldSavedData {

    private static final String DATA_NAME = NeoECOAE.MODID + "_storage_domains";
    private static final int DATA_VERSION = 1;

    private final Map<UUID, ECOStorageBackend> domains = new LinkedHashMap<UUID, ECOStorageBackend>();
    private final Map<UUID, Set<UUID>> committedSources = new LinkedHashMap<UUID, Set<UUID>>();

    public ECOStorageDomainData() {
        super(DATA_NAME);
    }

    public ECOStorageDomainData(String name) {
        super(name);
    }

    public static ECOStorageDomainData get(World world) {
        ECOStorageDomainData data = (ECOStorageDomainData) world.perWorldStorage
            .loadData(ECOStorageDomainData.class, DATA_NAME);
        if (data == null) {
            data = new ECOStorageDomainData();
            world.perWorldStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    public ECOStorageBackend getOrCreateDomain(UUID domainId) {
        ECOStorageBackend backend = this.domains.get(domainId);
        if (backend == null) {
            backend = new ECOStorageBackend(ECOCapacityPolicy.infinite());
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
        for (Map.Entry<ECOStorageKey, ECOAmount> entry : source.getEntriesView()
            .entrySet()) {
            domain.insert(entry.getKey(), entry.getValue(), false);
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
                NeoECOAE.LOG.error("Skipping unreadable ECO storage domain {}: {}", domainId, e.getMessage());
                continue;
            }
            this.domains.put(domainId, backend);
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
            if (entry.getValue()
                .isEmpty()) {
                continue;
            }
            NBTTagCompound domainTag = new NBTTagCompound();
            domainTag.setString(
                "id",
                entry.getKey()
                    .toString());
            NBTTagCompound storageTag = new NBTTagCompound();
            entry.getValue()
                .writeToNBT(storageTag);
            domainTag.setTag("storage", storageTag);
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
