package cn.dancingsnow.neoecoae.storage.domain;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.storage.core.ECOCapacityPolicy;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;

class ECOStorageDomainDataTest {

    @Test
    void emptyDomainIdentitySurvivesSaveAndLoad() {
        UUID domainId = UUID.randomUUID();
        ECOStorageDomainData original = new ECOStorageDomainData();
        original.getOrCreateDomain(domainId);
        NBTTagCompound tag = new NBTTagCompound();
        original.writeToNBT(tag);

        ECOStorageDomainData restored = new ECOStorageDomainData();
        restored.readFromNBT(tag);

        assertNotNull(restored.getDomain(domainId));
    }

    @Test
    void migrationCannotCommitAnUnreadableSourceAsEmpty() {
        ECOStorageDomainData domains = new ECOStorageDomainData();
        ECOStorageBackend source = new ECOStorageBackend(ECOCapacityPolicy.finite(64L, 2L));
        source.quarantine(new NBTTagCompound(), "broken source");

        assertThrows(
            IllegalStateException.class,
            () -> domains.commitDiskToDomain(UUID.randomUUID(), UUID.randomUUID(), source));
    }
}
