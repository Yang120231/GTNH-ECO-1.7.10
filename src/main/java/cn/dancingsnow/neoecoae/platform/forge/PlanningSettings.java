package cn.dancingsnow.neoecoae.platform.forge;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** The locked NEConfig defaults and ranges, expressed through Forge 1.7.10 configuration. */
public final class PlanningSettings {

    public static int ecoPlanningMaxMillis = 120_000, ecoPlanningMaxWork = 50_000_000, ecoPlanningMaxMemoryMiB = 64;
    public static boolean ecoPlanningStageDebug;
    public static int storageSystemMaxLength = 15;

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
        if (config.hasChanged()) config.save();
    }
}
