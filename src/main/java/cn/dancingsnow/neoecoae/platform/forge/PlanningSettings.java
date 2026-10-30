package cn.dancingsnow.neoecoae.platform.forge;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cn.dancingsnow.neoecoae.NeoECOAE;

/**
 * The locked NEConfig defaults and ranges, declared through GTNHLib's config system. Categories and keys match the
 * previous hand-written Forge configuration, so existing config/neoecoae.cfg files keep their values.
 */
public final class PlanningSettings {

    public static final int CRAFTING_WORKER_BASE_CRAFTS = 32;
    public static final int MAX_ECO_CPU_PUSH_TICK_LIMIT = 393_216;

    static final Class<?>[] CATEGORIES = { Multiblock.class, Storage.class, Crafting.class, Workstation.class,
        PatternUpload.class };

    private PlanningSettings() {}

    public static void register() throws ConfigException {
        for (Class<?> category : CATEGORIES) ConfigurationManager.registerConfig(category);
    }

    @Config(modid = NeoECOAE.MODID, category = "multiblock")
    public static final class Multiblock {

        @Config.Comment("Maximum storage-system length")
        @Config.DefaultInt(15)
        @Config.RangeInt(min = 4, max = Integer.MAX_VALUE)
        public static int storageSystemMaxLength = 15;

        @Config.Comment("Maximum allowed length of the crafting system multiblock, measured in blocks")
        @Config.DefaultInt(15)
        @Config.RangeInt(min = 5, max = Integer.MAX_VALUE)
        public static int craftingSystemMaxLength = 15;

        @Config.Comment("Maximum allowed length of the computation system multiblock, measured in blocks")
        @Config.DefaultInt(15)
        @Config.RangeInt(min = 5, max = Integer.MAX_VALUE)
        public static int computationSystemMaxLength = 15;
    }

    @Config(modid = NeoECOAE.MODID, category = "storage")
    public static final class Storage {

        @Config.Comment({
            "Apply AE2 network storage changes to the cached network list instead of re-listing every storage of the "
                + "network after each change. Networks that read other networks through storage buses keep AE2's behaviour." })
        @Config.DefaultBoolean(true)
        public static boolean incrementalNetworkStorageList = true;

        @Config.Comment("After incremental updates, rebuild the cached network list at most this often to absorb inaccurate "
            + "change reports from other mods")
        @Config.DefaultInt(200)
        @Config.RangeInt(min = 20, max = 72_000)
        public static int networkStorageListRepairTicks = 200;

        @Config.Comment("Time budget per tick, in microseconds, for migrating finite cells into infinite storage; at least one "
            + "resource type is still migrated every tick. / 有限存储盘迁移到无限存储时每 tick 的时间预算（微秒），"
            + "每 tick 至少仍迁移一种资源。")
        @Config.DefaultInt(2000)
        @Config.RangeInt(min = 1, max = 1_000_000)
        public static int infiniteMigrationMicrosPerTick = 2000;

        @Config.Comment("Maximum resource types migrated into infinite storage per tick, shared by all drives of a host. / "
            + "每 tick 最多迁移到无限存储的资源种类数，由同一主机的所有驱动器共享。")
        @Config.DefaultInt(512)
        @Config.RangeInt(min = 1, max = 1_000_000)
        public static int infiniteMigrationKeysPerTick = 512;
    }

    @Config(modid = NeoECOAE.MODID, category = "crafting")
    public static final class Crafting {

        @Config.Comment("Shared planning session time budget in milliseconds")
        @Config.DefaultInt(120_000)
        @Config.RangeInt(min = 1, max = 600_000)
        public static int ecoPlanningMaxMillis = 120_000;

        @Config.Comment("Shared checkpoint work budget")
        @Config.DefaultInt(50_000_000)
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public static int ecoPlanningMaxWork = 50_000_000;

        @Config.Comment("Retained cycle-solving memory budget in MiB")
        @Config.DefaultInt(64)
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public static int ecoPlanningMaxMemoryMiB = 64;

        @Config.Comment("Log planning stages")
        @Config.DefaultBoolean(false)
        public static boolean ecoPlanningStageDebug;

        @Config.Comment("Log source dispatch runtime diagnostics")
        @Config.DefaultBoolean(false)
        public static boolean ecoDispatchWatchdogDebug;

        @Config.Comment("Log blocked worker output delivery diagnostics")
        @Config.DefaultBoolean(false)
        public static boolean ecoCraftingOutputDeliveryDebug;

        // Unloaded settings (unit tests) keep the hard ceiling; the configured default is lower.
        @Config.Comment("Ordinary CPU dispatch safety limit; source algorithm applies its per-tick budget")
        @Config.DefaultInt(200_000)
        @Config.RangeInt(min = 1, max = MAX_ECO_CPU_PUSH_TICK_LIMIT)
        public static int ecoCpuPushTickLimit = MAX_ECO_CPU_PUSH_TICK_LIMIT;

        @Config.Comment("Verified source recipe cache limit")
        @Config.DefaultInt(512)
        @Config.RangeInt(min = 16, max = 16_384)
        public static int ecoFastPathCacheSize = 512;

        @Config.Comment("Post native crafting events; source rules then disable FastPath batching")
        @Config.DefaultBoolean(false)
        public static boolean postCraftingEvent;
    }

    @Config(modid = NeoECOAE.MODID, category = "workstation")
    public static final class Workstation {

        @Config.Comment("Enable large-workstation preheating and temperature protection; disabled uses coolant tiers instead")
        @Config.DefaultBoolean(true)
        public static boolean largeWorkstationTemperatureEnabled = true;
    }

    @Config(modid = NeoECOAE.MODID, category = "pattern_upload")
    public static final class PatternUpload {

        @Config.Comment("Horizontal placement of the upload buttons; 51 aligns with the terminal's right-side buttons")
        @Config.DefaultInt(51)
        @Config.RangeInt(min = -512, max = 512)
        public static int patternUploadButtonOffsetX = 51;

        @Config.Comment("Vertical offset of the upload buttons from the pattern terminal encode button")
        @Config.DefaultInt(9)
        @Config.RangeInt(min = -512, max = 512)
        public static int patternUploadButtonOffsetY = 9;
    }
}
