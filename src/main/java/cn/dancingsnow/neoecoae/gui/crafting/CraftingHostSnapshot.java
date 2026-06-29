package cn.dancingsnow.neoecoae.gui.crafting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.multiblock.ECOFormationBlockPos;
import cn.dancingsnow.neoecoae.tile.ECOControllerSubsystem;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import io.netty.buffer.ByteBuf;

public final class CraftingHostSnapshot {

    public static final CraftingHostSnapshot EMPTY = new CraftingHostSnapshot(
        false,
        false,
        "L4",
        "",
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0);

    public final boolean formed;
    public final boolean mirrored;
    public final String tier;
    public final String formationMessage;
    public final int memberCount;
    public final int patternCount;
    public final int workerCount;
    public final int parallelCoreCount;
    public final int inputCacheCount;
    public final int outputCacheCount;
    public final int runningTaskCount;
    public final int fastPathHitCount;
    public final int fastPathFallbackCount;
    public final int fastPathQueueDepth;
    public final int fastPathUtilizationPercent;
    public final int fastPathCapacity;

    private CraftingHostSnapshot(boolean formed, boolean mirrored, String tier, String formationMessage,
        int memberCount, int patternCount, int workerCount, int parallelCoreCount, int inputCacheCount,
        int outputCacheCount, int runningTaskCount, int fastPathHitCount, int fastPathFallbackCount,
        int fastPathQueueDepth, int fastPathUtilizationPercent, int fastPathCapacity) {
        this.formed = formed;
        this.mirrored = mirrored;
        this.tier = tier == null ? "" : tier;
        this.formationMessage = formationMessage == null ? "" : formationMessage;
        this.memberCount = safeInt(memberCount);
        this.patternCount = safeInt(patternCount);
        this.workerCount = safeInt(workerCount);
        this.parallelCoreCount = safeInt(parallelCoreCount);
        this.inputCacheCount = safeInt(inputCacheCount);
        this.outputCacheCount = safeInt(outputCacheCount);
        this.runningTaskCount = safeInt(runningTaskCount);
        this.fastPathHitCount = safeInt(fastPathHitCount);
        this.fastPathFallbackCount = safeInt(fastPathFallbackCount);
        this.fastPathQueueDepth = safeInt(fastPathQueueDepth);
        this.fastPathUtilizationPercent = Math.max(0, Math.min(100, fastPathUtilizationPercent));
        this.fastPathCapacity = safeInt(fastPathCapacity);
    }

    public static CraftingHostSnapshot create(TileECOController controller) {
        if (controller == null || controller.getSubsystem() != ECOControllerSubsystem.CRAFTING) {
            return EMPTY;
        }
        Counts counts = Counts.from(controller);
        Object backend = craftingBackend(controller);
        return new CraftingHostSnapshot(
            controller.isFormed(),
            controller.isMirrored(),
            controller.getTier()
                .name(),
            controller.getLastFormationMessage(),
            counts.memberCount,
            firstAvailableInt(controller, backend, 0, "getCraftingPatternCount", "getPatternCount", "patternCount"),
            firstAvailableInt(
                controller,
                backend,
                counts.workerCount,
                "getCraftingWorkerCount",
                "getWorkerCount",
                "workerCount"),
            firstAvailableInt(
                controller,
                backend,
                counts.parallelCoreCount,
                "getCraftingParallelCoreCount",
                "getParallelCoreCount",
                "parallelCoreCount"),
            firstAvailableInt(
                controller,
                backend,
                0,
                "getCraftingInputCacheCount",
                "getInputCacheCount",
                "inputCachedItems",
                "inputCacheCount"),
            firstAvailableInt(
                controller,
                backend,
                0,
                "getCraftingOutputCacheCount",
                "getOutputCacheCount",
                "outputCachedItems",
                "outputCacheCount"),
            firstAvailableInt(
                controller,
                backend,
                0,
                "getCraftingRunningTaskCount",
                "getRunningTaskCount",
                "runningWorkerCount",
                "runningTaskCount"),
            firstAvailableInt(
                controller,
                backend,
                0,
                "getCraftingFastPathHitCount",
                "getFastPathHitCount",
                "fastPathHitCount"),
            firstAvailableInt(
                controller,
                backend,
                0,
                "getCraftingFastPathFallbackCount",
                "getFastPathFallbackCount",
                "fastPathFallbackCount"),
            firstAvailableInt(
                controller,
                backend,
                0,
                "getCraftingFastPathQueueDepth",
                "getFastPathQueueDepth",
                "fastPathQueueDepth"),
            firstAvailableInt(
                controller,
                backend,
                0,
                "getCraftingFastPathUtilizationPercent",
                "getFastPathUtilizationPercent",
                "fastPathUtilizationPercent"),
            firstAvailableInt(
                controller,
                backend,
                counts.workerCount,
                "getCraftingFastPathCapacity",
                "getFastPathCapacity",
                "fastPathCapacity"));
    }

    public void write(ByteBuf buffer) {
        buffer.writeBoolean(this.formed);
        buffer.writeBoolean(this.mirrored);
        writeString(buffer, this.tier);
        writeString(buffer, this.formationMessage);
        buffer.writeInt(this.memberCount);
        buffer.writeInt(this.patternCount);
        buffer.writeInt(this.workerCount);
        buffer.writeInt(this.parallelCoreCount);
        buffer.writeInt(this.inputCacheCount);
        buffer.writeInt(this.outputCacheCount);
        buffer.writeInt(this.runningTaskCount);
        buffer.writeInt(this.fastPathHitCount);
        buffer.writeInt(this.fastPathFallbackCount);
        buffer.writeInt(this.fastPathQueueDepth);
        buffer.writeInt(this.fastPathUtilizationPercent);
        buffer.writeInt(this.fastPathCapacity);
    }

    public static CraftingHostSnapshot read(ByteBuf buffer) {
        return new CraftingHostSnapshot(
            buffer.readBoolean(),
            buffer.readBoolean(),
            readString(buffer),
            readString(buffer),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt());
    }

    private static Object craftingBackend(Object controller) {
        String[] names = { "getCraftingHostStats", "getCraftingStats", "getCraftingBackendStats", "getCraftingBackend",
            "getCraftingRuntime" };
        for (String name : names) {
            Object value = invokeObject(controller, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static int firstAvailableInt(Object primary, Object secondary, int fallback, String... names) {
        for (String name : names) {
            Integer value = invokeInt(primary, name);
            if (value != null) {
                return value.intValue();
            }
            value = readIntField(primary, name);
            if (value != null) {
                return value.intValue();
            }
            value = invokeInt(secondary, name);
            if (value != null) {
                return value.intValue();
            }
            value = readIntField(secondary, name);
            if (value != null) {
                return value.intValue();
            }
        }
        return fallback;
    }

    private static Object invokeObject(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass()
                .getMethod(name);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer invokeInt(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass()
                .getMethod(name);
            Object value = method.invoke(target);
            return numberValue(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer readIntField(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass()
                .getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return numberValue(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer numberValue(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf(safeInt(((Number) value).longValue()));
        }
        return null;
    }

    private static int safeInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 512);
        buffer.writeShort(length);
        buffer.writeBytes(bytes, 0, length);
    }

    private static String readString(ByteBuf buffer) {
        int length = Math.min(Math.max(0, buffer.readUnsignedShort()), 512);
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static final class Counts {

        private int memberCount;
        private int workerCount;
        private int parallelCoreCount;

        private static Counts from(TileECOController controller) {
            Counts counts = new Counts();
            World world = controller.getWorldObj();
            List<ECOFormationBlockPos> members = controller.getFormedMemberBlocks();
            counts.memberCount = members.size();
            if (world == null || !controller.isFormed()) {
                return counts;
            }
            for (ECOFormationBlockPos pos : members) {
                Block block = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
                if (block == NEBlocks.craftingWorker) {
                    counts.workerCount++;
                } else if (isParallelCore(block)) {
                    counts.parallelCoreCount++;
                }
            }
            return counts;
        }

        private static boolean isParallelCore(Block block) {
            return block == NEBlocks.craftingParallelCoreL4 || block == NEBlocks.craftingParallelCoreL6
                || block == NEBlocks.craftingParallelCoreL9;
        }

    }
}
