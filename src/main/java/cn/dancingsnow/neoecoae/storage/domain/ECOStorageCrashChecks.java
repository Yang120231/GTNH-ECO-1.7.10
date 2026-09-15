package cn.dancingsnow.neoecoae.storage.domain;

import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata;

/** Opt-in process-crash fixture, restricted to the isolated audit world. */
public final class ECOStorageCrashChecks {

    private ECOStorageCrashChecks() {}

    public static void run() {
        String phase = System.getProperty("neoecoae.storageCrashPhase", "");
        if (phase.isEmpty()) return;
        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        if (!"audit-world".equals(
            world.getWorldInfo()
                .getWorldName()))
            throw new IllegalStateException("Crash fixture requires audit-world");
        UUID domain = UUID.fromString("c1a31c99-39ee-4c63-930b-2cba4e20e658");
        UUID disk = UUID.fromString("f28582ce-9ef9-4574-b907-1e801c612319");
        ECOStorageDomainData data = ECOStorageDomainData.get(world);
        if (phase.equals("prepare")) {
            data.getOrCreateDomain(domain);
            NBTTagCompound plan = new NBTTagCompound();
            NBTTagCompound target = new NBTTagCompound();
            target.setLong("AuditAmount", 123456789L);
            plan.setTag(disk.toString(), target);
            data.beginRestore(domain, plan);
            data.saveDurably(world);
            NeoECOAE.LOG.info("ECO crash fixture: prepared journal durable; halting JVM");
            awaitExternalKill();
        }
        NBTTagCompound saved = data.getRestorePlan(domain);
        if (saved == null || saved.getCompoundTag(disk.toString())
            .getLong("AuditAmount") != 123456789L) throw new IllegalStateException("Crash lost target snapshot");
        if (phase.equals("commit")) {
            data.completeRestore(domain);
            data.saveDurably(world);
            NeoECOAE.LOG.info("ECO crash fixture: completed journal durable; halting JVM");
            awaitExternalKill();
        }
        if (!phase.equals("verify")) throw new IllegalArgumentException("Unknown crash phase");
        ItemStack stale = new ItemStack(Items.iron_ingot);
        ECOStorageCellMetadata.markDomainMember(stale, domain, 0);
        stale.getTagCompound()
            .setString("ECODiskId", disk.toString());
        if (!data.recoverRestoredMember(stale) || stale.getTagCompound()
            .getCompoundTag("ECOStorage")
            .getLong("AuditAmount") != 123456789L || data.recoverRestoredMember(stale))
            throw new IllegalStateException("Non-idempotent crash recovery");
        NeoECOAE.LOG
            .info("ECO process crash recovery checks PASSED: prepare, commit, stale member replay, no portable replay");
    }

    private static void awaitExternalKill() {
        // Forge traps Runtime.halt. The harness kills this isolated process
        // after observing the durable checkpoint, without normal shutdown saves.
        for (;;) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000_000L);
    }
}
