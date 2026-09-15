package cn.dancingsnow.neoecoae.storage.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.storage.core.ECOCapacityPolicy;
import cn.dancingsnow.neoecoae.storage.core.ECOStorageBackend;

class ECOStorageDomainDataTest {

    @Test
    void completedJournalRepairsStaleMemberButNeverReplaysPortableCell() {
        UUID domain = UUID.randomUUID();
        net.minecraft.item.ItemStack cell = new net.minecraft.item.ItemStack(new net.minecraft.item.Item());
        UUID disk = cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata.getOrCreateDiskId(cell);
        cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata.markDomainMember(cell, domain, 0);
        ECOStorageDomainData data = new ECOStorageDomainData();
        data.getOrCreateDomain(domain);
        ECOStorageBackend target = new ECOStorageBackend(ECOCapacityPolicy.finite(10000, 0));
        cn.dancingsnow.neoecoae.storage.core.ECOStorageKey key = cn.dancingsnow.neoecoae.storage.core.ECOStorageKey
            .item("minecraft:iron_ingot", 0, "");
        target.insert(key, cn.dancingsnow.neoecoae.storage.core.ECOAmount.of(123), false);
        NBTTagCompound contents = new NBTTagCompound();
        target.writeToNBT(contents);
        NBTTagCompound plan = new NBTTagCompound();
        plan.setTag(disk.toString(), contents);
        data.beginRestore(domain, plan);
        data.completeRestore(domain);
        assertFalse(data.recoverRestoredMember(cell));
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        data.readFromNBT(saved);
        data.removeDomain(domain);
        assertTrue(data.recoverRestoredMember(cell));
        ECOStorageBackend restored = cn.dancingsnow.neoecoae.storage.item.ECOStorageCellAccess.load(cell);
        org.junit.jupiter.api.Assertions.assertEquals(
            123,
            restored.getAmount(key)
                .toLongSaturated());
        cell.getTagCompound()
            .removeTag("ECOStorage");
        assertFalse(data.recoverRestoredMember(cell));
    }

    @Test
    void restoreJournalSurvivesBothInterruptionPhasesAndIsDefensive() {
        UUID id = UUID.randomUUID();
        ECOStorageDomainData data = new ECOStorageDomainData();
        data.getOrCreateDomain(id);
        NBTTagCompound plan = new NBTTagCompound();
        plan.setString("target", "snapshot");
        data.beginRestore(id, plan);
        plan.setBoolean("Completed", true);
        assertFalse(
            data.getRestorePlan(id)
                .getBoolean("Completed"));
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        ECOStorageDomainData recovered = new ECOStorageDomainData();
        recovered.readFromNBT(saved);
        assertFalse(
            recovered.getRestorePlan(id)
                .getBoolean("Completed"));
        recovered.completeRestore(id);
        recovered.completeRestore(id);
        recovered.writeToNBT(saved);
        data.readFromNBT(saved);
        assertTrue(
            data.getRestorePlan(id)
                .getBoolean("Completed"));
        assertTrue(data.isDomainEmpty(id));
    }

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
