package cn.dancingsnow.neoecoae.platform.forge;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** The locked NEConfig defaults and ranges, expressed through Forge 1.7.10 configuration. */
public final class PlanningSettings {

    public static int ecoPlanningMaxMillis = 120_000, ecoPlanningMaxWork = 50_000_000, ecoPlanningMaxMemoryMiB = 64;
    public static boolean ecoPlanningStageDebug;
    public static boolean ecoDispatchWatchdogDebug;
    public static boolean ecoCraftingOutputDeliveryDebug;
    public static int storageSystemMaxLength = 15, computationSystemMaxLength = 15, craftingSystemMaxLength = 15;
    public static final int CRAFTING_WORKER_BASE_CRAFTS = 32;
    public static final int MAX_ECO_CPU_PUSH_TICK_LIMIT = 393_216;
    public static int ecoCpuPushTickLimit = MAX_ECO_CPU_PUSH_TICK_LIMIT;
    public static int ecoFastPathCacheSize = 512;
    public static boolean postCraftingEvent;
    public static boolean largeWorkstationTemperatureEnabled = true;
    public static int patternUploadButtonOffsetX = 51, patternUploadButtonOffsetY = 9;
    public static boolean incrementalNetworkStorageList = true;
    public static int networkStorageListRepairTicks = 200;
    public static int infiniteMigrationMicrosPerTick = 2000;
    public static int infiniteMigrationKeysPerTick = 512;

    private PlanningSettings() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        storageSystemMaxLength = config
            .getInt("storageSystemMaxLength", "multiblock", 15, 4, Integer.MAX_VALUE, "Maximum storage-system length");
        incrementalNetworkStorageList = config.getBoolean(
            "incrementalNetworkStorageList",
            "storage",
            true,
            "Apply AE2 network storage changes to the cached network list instead of re-listing every storage of the "
                + "network after each change. Networks that read other networks through storage buses keep AE2's behaviour.");
        networkStorageListRepairTicks = config.getInt(
            "networkStorageListRepairTicks",
            "storage",
            200,
            20,
            72_000,
            "After incremental updates, rebuild the cached network list at most this often to absorb inaccurate "
                + "change reports from other mods");
        infiniteMigrationMicrosPerTick = config.getInt(
            "infiniteMigrationMicrosPerTick",
            "storage",
            2000,
            1,
            1_000_000,
            "Time budget per tick, in microseconds, for migrating finite cells into infinite storage; at least one "
                + "resource type is still migrated every tick. / 有限存储盘迁移到无限存储时每 tick 的时间预算（微秒），"
                + "每 tick 至少仍迁移一种资源。");
        infiniteMigrationKeysPerTick = config.getInt(
            "infiniteMigrationKeysPerTick",
            "storage",
            512,
            1,
            1_000_000,
            "Maximum resource types migrated into infinite storage per tick, shared by all drives of a host. / "
                + "每 tick 最多迁移到无限存储的资源种类数，由同一主机的所有驱动器共享。");
        craftingSystemMaxLength = config.getInt(
            "craftingSystemMaxLength",
            "multiblock",
            15,
            5,
            Integer.MAX_VALUE,
            "Maximum allowed length of the crafting system multiblock, measured in blocks");
        computationSystemMaxLength = config.getInt(
            "computationSystemMaxLength",
            "multiblock",
            15,
            5,
            Integer.MAX_VALUE,
            "Maximum allowed length of the computation system multiblock, measured in blocks");
        ecoPlanningMaxMillis = config.getInt(
            "ecoPlanningMaxMillis",
            "crafting",
            120_000,
            1,
            600_000,
            "Shared planning session time budget in milliseconds");
        ecoPlanningMaxWork = config.getInt(
            "ecoPlanningMaxWork",
            "crafting",
            50_000_000,
            1,
            Integer.MAX_VALUE,
            "Shared checkpoint work budget");
        ecoPlanningMaxMemoryMiB = config.getInt(
            "ecoPlanningMaxMemoryMiB",
            "crafting",
            64,
            1,
            Integer.MAX_VALUE,
            "Retained cycle-solving memory budget in MiB");
        ecoPlanningStageDebug = config.getBoolean("ecoPlanningStageDebug", "crafting", false, "Log planning stages");
        ecoDispatchWatchdogDebug = config
            .getBoolean("ecoDispatchWatchdogDebug", "crafting", false, "Log source dispatch runtime diagnostics");
        ecoCraftingOutputDeliveryDebug = config.getBoolean(
            "ecoCraftingOutputDeliveryDebug",
            "crafting",
            false,
            "Log blocked worker output delivery diagnostics");
        ecoCpuPushTickLimit = config.getInt(
            "ecoCpuPushTickLimit",
            "crafting",
            200_000,
            1,
            MAX_ECO_CPU_PUSH_TICK_LIMIT,
            "Ordinary CPU dispatch safety limit; source algorithm applies its per-tick budget");
        ecoFastPathCacheSize = config
            .getInt("ecoFastPathCacheSize", "crafting", 512, 16, 16_384, "Verified source recipe cache limit");
        postCraftingEvent = config.getBoolean(
            "postCraftingEvent",
            "crafting",
            false,
            "Post native crafting events; source rules then disable FastPath batching");
        largeWorkstationTemperatureEnabled = config.getBoolean(
            "largeWorkstationTemperatureEnabled",
            "workstation",
            true,
            "Enable large-workstation preheating and temperature protection; disabled uses coolant tiers instead");
        patternUploadButtonOffsetX = config.getInt(
            "patternUploadButtonOffsetX",
            "pattern_upload",
            51,
            -512,
            512,
            "Horizontal offset of the upload buttons from the pattern terminal encode button");
        patternUploadButtonOffsetY = config.getInt(
            "patternUploadButtonOffsetY",
            "pattern_upload",
            9,
            -512,
            512,
            "Vertical offset of the upload buttons from the pattern terminal encode button");
        if (config.hasChanged()) config.save();
    }
}
