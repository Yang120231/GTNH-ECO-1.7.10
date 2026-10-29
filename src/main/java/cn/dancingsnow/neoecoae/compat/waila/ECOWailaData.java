package cn.dancingsnow.neoecoae.compat.waila;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import appeng.core.localization.GuiText;
import appeng.core.localization.WailaText;
import cn.dancingsnow.neoecoae.api.computation.ECOComputationCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;

/**
 * The 1.21 Jade providers' server data and tooltip lines, kept free of WAILA types so both halves are testable. The
 * server writes one {@value #TAG} compound; the client only formats what it receives.
 */
public final class ECOWailaData {

    static final String TAG = "neoecoae";
    private static final String INFINITY = "∞";

    private ECOWailaData() {}

    public static NBTTagCompound write(TileEntity tile) {
        NBTTagCompound data = new NBTTagCompound();
        if (tile instanceof ECODriveBlockEntity drive) writeDrive(drive, data);
        else if (tile instanceof ECOStorageSystemBlockEntity host) writeStorageHost(host, data);
        else if (tile instanceof ECOMachineInterfaceBlockEntity<?>machine
            && machine.getCluster() instanceof NEStorageCluster) {
                ECOStorageInterfaceMode mode = machine.getStorageInterfaceMode();
                data.setInteger("storageMode", (mode == null ? ECOStorageInterfaceMode.STORAGE : mode).ordinal());
            } else if (tile instanceof ECOComputationSystemBlockEntity system) {
                data.setBoolean("fastPlannerEnabled", system.isNetworkFastPlannerEnabled());
                data.setBoolean("cyclePlanningEnabled", system.isNetworkCyclePlanningEnabled());
            } else if (tile instanceof ECOComputationDriveBlockEntity drive) writeComputationDrive(drive, data);
        return data;
    }

    private static void writeDrive(ECODriveBlockEntity drive, NBTTagCompound data) {
        data.setBoolean("infiniteMember", ECOInfiniteStorageMember.isMember(drive.getCellStack()));
        data.setBoolean("infiniteResource", drive.getCellInventory() instanceof ECOInfiniteResourceCell);
        data.setBoolean("mounted", drive.isMounted());
        // The synchronized field the status LED reads, not the live node, so a stale value is visible here.
        data.setBoolean("online", drive.isOnline());
        IECOStorageCell cell = drive.getCellInventory();
        if (cell == null) return;
        var controller = drive.getStorageController();
        data.setBoolean(
            "tierMismatch",
            controller != null && controller.getTier()
                .compareTo(cell.getTier()) < 0);
        data.setLong("usedBytes", cell.getUsedBytes());
        data.setLong("totalBytes", cell.getTotalBytes());
        data.setLong("storedItemTypes", cell.getStoredItemTypes());
        data.setLong("totalItemTypes", cell.getTotalItemTypes());
        data.setBoolean("infiniteTypeCapacity", cell.hasInfiniteTypeCapacity());
    }

    private static void writeStorageHost(ECOStorageSystemBlockEntity host, NBTTagCompound data) {
        if (!host.isFormed()) return;
        if (host.isFormedInfiniteMode()) data.setString("ecoStorageDomain", host.getInfiniteDomainText());
        if (host.isMigratingToInfinite()) {
            data.setInteger("migrationProgress", host.getInfiniteMigrationProgressPercent());
            String blocked = host.getInfiniteMigrationBlockedReason();
            if (blocked != null) data.setString("migrationBlocked", blocked);
        }
        data.setString(
            "hostUsedBytes",
            host.getTotalUsedBytes()
                .toString());
        BigInteger capacity = host.getTotalCapacityBytes();
        if (capacity != null) data.setString("hostTotalBytes", capacity.toString());
    }

    private static void writeComputationDrive(ECOComputationDriveBlockEntity drive, NBTTagCompound data) {
        var cluster = drive.getCluster();
        var controller = cluster == null ? null : cluster.getController();
        var memory = ECOComputationCells.describe(drive.getCellStack());
        if (controller != null && memory != null) data.setBoolean(
            "tierMismatch",
            controller.getTier()
                .compareTo(memory.tier()) < 0);
    }

    public static void appendLines(NBTTagCompound data, List<String> lines) {
        if (data.hasKey("mounted")) appendDrive(data, lines);
        else if (data.hasKey("hostUsedBytes")) appendStorageHost(data, lines);
        else if (data.hasKey("storageMode")) appendStorageInterface(data, lines);
        else if (data.hasKey("fastPlannerEnabled")) appendComputationSystem(data, lines);
        else if (data.getBoolean("tierMismatch"))
            lines.add(EnumChatFormatting.RED + translate("jade.neoecoae.computation_cell_tier_too_high"));
    }

    private static void appendDrive(NBTTagCompound data, List<String> lines) {
        if (data.getBoolean("tierMismatch"))
            lines.add(EnumChatFormatting.RED + translate("jade.neoecoae.storage_matrix_tier_too_high"));
        if (data.getBoolean("infiniteMember")) {
            lines.add(EnumChatFormatting.LIGHT_PURPLE + translate("tooltip.neoecoae.storage.infinite_member"));
            return;
        }
        if (data.hasKey("online") && !data.getBoolean("online"))
            lines.add(EnumChatFormatting.RED + translate(WailaText.DeviceOffline.getUnlocalized()));
        boolean mounted = data.getBoolean("mounted");
        if (data.getBoolean("infiniteResource")) {
            if (mounted) lines.add(EnumChatFormatting.GREEN + translate("jade.neoecoae.drive_mounted"));
            return;
        }
        if (!mounted) {
            lines.add(EnumChatFormatting.RED + translate("jade.neoecoae.drive_unmounted"));
            return;
        }
        lines.add(EnumChatFormatting.GREEN + translate("jade.neoecoae.drive_mounted"));
        if (data.hasKey("usedBytes")) {
            long total = data.getLong("totalBytes");
            lines.add(
                bytesUsed(
                    BigInteger.valueOf(data.getLong("usedBytes")),
                    total == Long.MAX_VALUE ? null : BigInteger.valueOf(total)));
        }
        if (data.hasKey("storedItemTypes")) {
            String total = data.getBoolean("infiniteTypeCapacity") ? INFINITY
                : format(BigInteger.valueOf(data.getLong("totalItemTypes")));
            lines.add(
                format(BigInteger.valueOf(data.getLong("storedItemTypes"))) + " "
                    + GuiText.Of.getLocal()
                    + " "
                    + total
                    + " "
                    + GuiText.Types.getLocal());
        }
    }

    private static void appendStorageHost(NBTTagCompound data, List<String> lines) {
        String domain = data.getString("ecoStorageDomain");
        if (!domain.isEmpty()) {
            lines.add(EnumChatFormatting.LIGHT_PURPLE + translate("jade.neoecoae.storage.infinite_enabled"));
            lines.add(EnumChatFormatting.AQUA + "UUID: " + domain);
        }
        if (data.hasKey("migrationProgress")) {
            lines.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.storage.ui.migration_progress",
                    data.getInteger("migrationProgress") + "%"));
            if (data.hasKey("migrationBlocked")) lines.add(
                EnumChatFormatting.RED + StatCollector.translateToLocalFormatted(
                    "gui.neoecoae.storage.ui.migration_blocked",
                    translate(data.getString("migrationBlocked"))));
        }
        lines.add(
            bytesUsed(
                new BigInteger(data.getString("hostUsedBytes")),
                data.hasKey("hostTotalBytes") ? new BigInteger(data.getString("hostTotalBytes")) : null));
    }

    private static void appendStorageInterface(NBTTagCompound data, List<String> lines) {
        ECOStorageInterfaceMode[] modes = ECOStorageInterfaceMode.values();
        ECOStorageInterfaceMode mode = modes[Math.max(0, Math.min(modes.length - 1, data.getInteger("storageMode")))];
        if (mode == ECOStorageInterfaceMode.INPUT)
            lines.add(EnumChatFormatting.BLUE + translate("jade.neoecoae.storage_interface.input"));
        else if (mode == ECOStorageInterfaceMode.OUTPUT)
            lines.add(EnumChatFormatting.GOLD + translate("jade.neoecoae.storage_interface.output"));
    }

    private static void appendComputationSystem(NBTTagCompound data, List<String> lines) {
        boolean fast = data.getBoolean("fastPlannerEnabled");
        lines.add(
            (fast ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                + translate(fast ? "jade.neoecoae.fast_planner.enabled" : "jade.neoecoae.fast_planner.disabled"));
        boolean cycle = data.getBoolean("cyclePlanningEnabled");
        lines.add(
            (cycle ? EnumChatFormatting.AQUA : EnumChatFormatting.GRAY)
                + translate(cycle ? "jade.neoecoae.cycle_planning.enabled" : "jade.neoecoae.cycle_planning.disabled"));
    }

    /** AE2's own cell wording, with the exact grouped byte counts; {@code null} total is unbounded. */
    static String bytesUsed(BigInteger used, BigInteger total) {
        return format(used) + " "
            + GuiText.Of.getLocal()
            + " "
            + (total == null ? INFINITY : format(total))
            + " "
            + GuiText.BytesUsed.getLocal();
    }

    private static String format(BigInteger value) {
        return NumberFormat.getInstance()
            .format(value.signum() < 0 ? BigInteger.ZERO : value);
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }
}
