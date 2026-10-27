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

    private PlanningSettings() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        storageSystemMaxLength = config
            .getInt("storageSystemMaxLength", "multiblock", 15, 4, Integer.MAX_VALUE, "Maximum storage-system length");
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
        if (config.hasChanged()) config.save();
    }
}
