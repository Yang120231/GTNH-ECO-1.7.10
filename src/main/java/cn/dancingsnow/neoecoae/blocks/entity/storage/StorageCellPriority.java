package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;

/** Resolves only the same installed cell in a loaded drive of the still-formed host. */
public final class StorageCellPriority {

    private StorageCellPriority() {}

    @Nullable
    public static ECODriveBlockEntity resolve(ECOStorageSystemBlockEntity host, BlockPos position, UUID expected) {
        if (position == null || expected == null
            || !host.isFormed()
            || host.getCluster() == null
            || host.getLevel() == null
            || host.getLevel().isClientSide
            || !host.getLevel()
                .hasChunkAt(position))
            return null;
        for (var drive : host.getCluster()
            .getDrives()) {
            if (!drive.isRemoved() && position.equals(drive.getBlockPos())
                && drive.getStorageController() == host
                && expected.equals(drive.getCellInteractionId())
                && host.getLevel()
                    .getBlockEntity(position) == drive
                && ECOStorageCellSettings.canConfigure(drive.getCellStack())
                && drive.getCellInventory() != null) return drive;
        }
        return null;
    }

    public static boolean set(ECOStorageSystemBlockEntity host, BlockPos position, UUID expected, int priority) {
        var drive = resolve(host, position, expected);
        if (drive == null) return false;
        drive.setConfiguredCellPriority(priority);
        return true;
    }
}
