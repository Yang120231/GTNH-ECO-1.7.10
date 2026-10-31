package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachment;
import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachmentRegistry;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingDispatchEvent;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechMachineStatus;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.nbt.ListTag;
import cn.dancingsnow.neoecoae.platform.nbt.Tag;

/** Destination records survive reload; all live progress is read-only and refreshed only by an open view. */
public final class ECOProcessingMonitor implements ECOCraftingJobAttachment {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("neoecoae", "processing_monitor");
    static final int READS_PER_CPU_TICK = 32, READS_PER_SERVER_TICK = 256, REFRESH_INTERVAL = 10;
    private static final Map<MinecraftServer, Budget> SERVER_BUDGETS = new WeakHashMap<>();
    private final Map<AEKey, LinkedHashSet<Target>> targets = new LinkedHashMap<>();
    private final Map<AEKey, Survey> surveys = new LinkedHashMap<>();
    private final ArrayDeque<Survey> pending = new ArrayDeque<>();
    private UUID jobId;
    private long budgetTick = Long.MIN_VALUE;
    private int readsRemaining;
    private boolean failureReported;

    public static void initialize() {
        ECOCraftingJobAttachmentRegistry.register(ID, context -> {
            if (!(context.cpu() instanceof ECOCraftingCPU cpu)) return null;
            var monitor = cpu.getLogic()
                .getProcessingMonitor();
            monitor.bind(context.craftingJobId());
            return monitor;
        });
    }

    private void bind(UUID id) {
        if (id.equals(jobId)) return;
        reset();
        jobId = id;
    }

    void dispatched(ECOCraftingDispatchEvent event) {
        try {
            bind(
                event.job()
                    .craftingJobId());
            var destinations = ECOGregTechMachineStatus.targets(event.provider());
            for (var output : event.pattern()
                .getOutputs()) {
                if (output.amount() <= 0) continue;
                for (Target target : destinations) observe(output.what(), target);
            }
        } catch (RuntimeException | LinkageError incompatible) {
            report(incompatible);
        }
    }

    /**
     * Native-execution counterpart of {@link #dispatched}: AE2-planned jobs push through the native medium directly and
     * fire no ECO dispatch event, so the vanilla executor reports the accepted destinations here.
     */
    void dispatchedNative(UUID craftingJobId, appeng.api.networking.crafting.ICraftingMedium medium,
        appeng.api.networking.crafting.ICraftingPatternDetails details) {
        try {
            bind(craftingJobId);
            var destinations = cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider
                .processingTargets(medium);
            if (destinations.isEmpty()) return;
            for (var output : details.getCondensedAEOutputs()) {
                if (output == null || output.getStackSize() <= 0) continue;
                AEKey key = AEKey.fromNative(output);
                if (key == null) continue;
                for (Target target : destinations) observe(key, target);
            }
        } catch (RuntimeException | LinkageError incompatible) {
            report(incompatible);
        }
    }

    void observe(AEKey output, Target target) {
        if (!targets.computeIfAbsent(output, ignored -> new LinkedHashSet<>())
            .add(target)) return;
        var survey = surveys.get(output);
        if (survey != null) {
            survey.nextTick = Long.MIN_VALUE;
            if (survey.scan != null) survey.scan.addRoot(target);
        }
    }

    public ECOProcessingProgress sample(World level, AEKey output, long tick) {
        var destinations = targets.get(output);
        if (level == null || destinations == null) return ECOProcessingProgress.EMPTY;
        var survey = surveys.computeIfAbsent(output, Survey::new);
        survey.requestedTick = tick;
        if (survey.scan == null && tick >= survey.nextTick) {
            survey.scan = new ECOGregTechMachineStatus.Scan(destinations, output);
        }
        if (survey.scan != null && !survey.queued) {
            pending.addLast(survey);
            survey.queued = true;
        }
        advance(level, tick);
        return survey.published.refreshing(survey.scan != null);
    }

    /** Recorded push destinations of an output (input buses, hatches, adjacent machines), as accepted by providers. */
    public List<Target> destinations(AEKey output) {
        var recorded = targets.get(output);
        return recorded == null ? cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf()
            : cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(recorded);
    }

    /** Actual recipe controllers resolved by the same budgeted status scan, never the pattern holder or input bus. */
    public List<Target> machineTargets(AEKey output) {
        var survey = surveys.get(output);
        if (survey == null) return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf();
        var resolved = new LinkedHashSet<>(survey.machineTargets);
        if (survey.scan != null) resolved.addAll(survey.scan.machineTargets());
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(resolved);
    }

    /** Called by visible menus. Repeated viewers of the same CPU share both samples and the per-tick budget. */
    public void advance(World level, long tick) {
        if (level == null || level.isRemote || MinecraftServer.getServer() == null) return;
        if (budgetTick != tick) {
            budgetTick = tick;
            readsRemaining = READS_PER_CPU_TICK;
        }
        while (readsRemaining > 0 && !pending.isEmpty()) {
            var survey = pending.peekFirst();
            if (surveys.get(survey.output) != survey || tick - survey.requestedTick > 20) {
                pending.removeFirst();
                survey.queued = false;
                continue;
            }
            if (!claim(
                MinecraftServer.getServer(),
                MinecraftServer.getServer()
                    .getTickCounter()))
                return;
            readsRemaining--;
            pending.removeFirst();
            survey.queued = false;
            try {
                survey.scan.step(level);
                if (survey.scan.complete()) {
                    survey.machineTargets = survey.scan.machineTargets();
                    survey.published = ECOProcessingProgress.summarize(survey.scan.machines());
                    survey.scan = null;
                    survey.nextTick = tick + REFRESH_INTERVAL;
                } else {
                    pending.addLast(survey);
                    survey.queued = true;
                }
            } catch (RuntimeException | LinkageError incompatible) {
                survey.scan = null;
                survey.nextTick = tick + 100;
                report(incompatible);
            }
        }
    }

    private static synchronized boolean claim(MinecraftServer server, long tick) {
        Budget budget = SERVER_BUDGETS.computeIfAbsent(server, ignored -> new Budget());
        if (budget.tick != tick) {
            budget.tick = tick;
            budget.remaining = READS_PER_SERVER_TICK;
        }
        if (budget.remaining <= 0) return false;
        budget.remaining--;
        return true;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public CompoundTag save(cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        var entries = new ListTag();
        targets.forEach((key, destinations) -> {
            var entry = new CompoundTag();
            entry.put("key", key.toTagGeneric(registries));
            var list = new ListTag();
            for (var target : destinations) {
                var destination = new CompoundTag();
                destination.putInt("dimension", target.dimension());
                destination.putLong(
                    "position",
                    target.position()
                        .asLong());
                list.add(destination);
            }
            entry.put("targets", list);
            entries.add(entry);
        });
        if (!entries.isEmpty()) tag.put("entries", entries);
        return tag;
    }

    @Override
    public void load(CompoundTag tag, cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider registries) {
        targets.clear();
        surveys.clear();
        pending.clear();
        var entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
            if (key == null) continue;
            var list = entry.getList("targets", Tag.TAG_COMPOUND);
            for (int j = 0; j < list.size(); j++) {
                var destination = list.getCompound(j);
                if (destination.contains("dimension", Tag.TAG_INT)) observe(
                    key,
                    new Target(destination.getInt("dimension"), BlockPos.of(destination.getLong("position"))));
            }
        }
    }

    @Override
    public void clear(ECOCraftingJobResult result) {
        reset();
    }

    private void reset() {
        targets.clear();
        surveys.clear();
        pending.clear();
        jobId = null;
        budgetTick = Long.MIN_VALUE;
        failureReported = false;
    }

    private void report(Throwable failure) {
        if (failureReported) return;
        failureReported = true;
        org.apache.logging.log4j.LogManager.getLogger("neoecoae")
            .warn("Optional processing status could not be read; crafting continues normally", failure);
    }

    private static final class Survey {

        final AEKey output;
        ECOGregTechMachineStatus.Scan scan;
        ECOProcessingProgress published = ECOProcessingProgress.EMPTY;
        List<Target> machineTargets = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf();
        long nextTick, requestedTick;
        boolean queued;

        Survey(AEKey output) {
            this.output = output;
        }
    }

    private static final class Budget {

        long tick = Long.MIN_VALUE;
        int remaining;
    }
}
