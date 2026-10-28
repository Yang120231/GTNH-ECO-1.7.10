package cn.dancingsnow.neoecoae.compat.waila;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;

class ECOWailaDataTest {

    private static List<String> lines(NBTTagCompound data) {
        List<String> lines = new ArrayList<>();
        ECOWailaData.appendLines(data, lines);
        return lines;
    }

    private static String grouped(long value) {
        return NumberFormat.getInstance()
            .format(value);
    }

    @Test
    void mountedDriveShowsExactBytesAndTypes() {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("mounted", true);
        data.setLong("usedBytes", 1_234_567L);
        data.setLong("totalBytes", 268_435_456L);
        data.setLong("storedItemTypes", 12L);
        data.setLong("totalItemTypes", 63L);
        List<String> lines = lines(data);
        assertEquals(3, lines.size());
        assertTrue(
            lines.get(0)
                .contains("jade.neoecoae.drive_mounted"));
        assertTrue(
            lines.get(1)
                .startsWith(grouped(1_234_567L) + " "),
            lines.get(1));
        assertTrue(
            lines.get(1)
                .contains(" " + grouped(268_435_456L) + " "),
            lines.get(1));
        assertTrue(
            lines.get(2)
                .startsWith("12 "),
            lines.get(2));
        assertTrue(
            lines.get(2)
                .contains(" 63 "),
            lines.get(2));
    }

    @Test
    void unboundedCapacitiesShowInfinity() {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("mounted", true);
        data.setLong("usedBytes", 5L);
        data.setLong("totalBytes", Long.MAX_VALUE);
        data.setLong("storedItemTypes", 1L);
        data.setLong("totalItemTypes", 0L);
        data.setBoolean("infiniteTypeCapacity", true);
        List<String> lines = lines(data);
        assertTrue(
            lines.get(1)
                .contains("∞"));
        assertTrue(
            lines.get(2)
                .contains("∞"));
    }

    @Test
    void unmountedDriveStopsAfterTheReason() {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("mounted", false);
        data.setBoolean("tierMismatch", true);
        data.setLong("usedBytes", 1L);
        data.setLong("totalBytes", 2L);
        List<String> lines = lines(data);
        assertEquals(2, lines.size());
        assertTrue(
            lines.get(0)
                .contains("storage_matrix_tier_too_high"));
        assertTrue(
            lines.get(1)
                .contains("drive_unmounted"));
    }

    /** A mounted drive whose synchronized online flag is stale must say so: that flag gates the status LED. */
    @Test
    void offlineDriveIsReportedBeforeItsMountState() {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("mounted", true);
        data.setBoolean("online", false);
        List<String> lines = lines(data);
        assertEquals(2, lines.size());
        assertTrue(
            lines.get(0)
                .contains(appeng.core.localization.WailaText.DeviceOffline.getUnlocalized()),
            lines.get(0));
        assertTrue(
            lines.get(1)
                .contains("drive_mounted"));
        data.setBoolean("online", true);
        assertEquals(1, lines(data).size());
    }

    @Test
    void hostShowsTotalsBeyondLongRange() {
        BigInteger used = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(BigInteger.TEN);
        NBTTagCompound data = new NBTTagCompound();
        data.setString("hostUsedBytes", used.toString());
        data.setString(
            "hostTotalBytes",
            used.multiply(BigInteger.valueOf(2))
                .toString());
        List<String> lines = lines(data);
        assertEquals(1, lines.size());
        assertTrue(
            lines.get(0)
                .startsWith(
                    NumberFormat.getInstance()
                        .format(used)));
    }

    @Test
    void infiniteHostShowsDomainAndUnboundedTotal() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("ecoStorageDomain", "domain-id");
        data.setString("hostUsedBytes", "7");
        List<String> lines = lines(data);
        assertEquals(3, lines.size());
        assertTrue(
            lines.get(1)
                .endsWith("UUID: domain-id"));
        assertTrue(
            lines.get(2)
                .contains("∞"));
    }

    @Test
    void interfaceAndComputationLines() {
        NBTTagCompound input = new NBTTagCompound();
        input.setInteger("storageMode", ECOStorageInterfaceMode.INPUT.ordinal());
        assertTrue(
            lines(input).get(0)
                .contains("storage_interface.input"));
        NBTTagCompound storage = new NBTTagCompound();
        storage.setInteger("storageMode", ECOStorageInterfaceMode.STORAGE.ordinal());
        assertTrue(lines(storage).isEmpty());
        NBTTagCompound computation = new NBTTagCompound();
        computation.setBoolean("fastPlannerEnabled", true);
        computation.setBoolean("cyclePlanningEnabled", false);
        List<String> lines = lines(computation);
        assertTrue(
            lines.get(0)
                .contains("fast_planner.enabled"));
        assertTrue(
            lines.get(1)
                .contains("cycle_planning.disabled"));
        assertTrue(lines(new NBTTagCompound()).isEmpty());
    }
}
