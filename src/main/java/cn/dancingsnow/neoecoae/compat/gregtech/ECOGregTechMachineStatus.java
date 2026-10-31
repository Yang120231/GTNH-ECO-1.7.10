package cn.dancingsnow.neoecoae.compat.gregtech;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;
import cn.dancingsnow.neoecoae.platform.gt.NativeProcessingMachines;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/**
 * Optional GTNH status reader. GT types stay behind {@link NativeGregTech}; no inventory simulations, recipe
 * retries or chunk loads.
 */
public final class ECOGregTechMachineStatus {

    private ECOGregTechMachineStatus() {}

    /**
     * A GT recipe controller's current cycle, read through the typed GT API.
     *
     * @param outputs           the cycle's pending ItemStack and FluidStack outputs
     * @param formed            false for an unformed multiblock controller
     * @param failure           the last recipe check's message when it failed, otherwise null
     * @param shutdown          the machine's last shutdown reason when it was shut down, otherwise null
     * @param maintenanceIssues missing maintenance fixes of a multiblock (ideal minus repair status)
     * @param outputBlocked     a basic machine cannot empty its output slots
     */
    @com.github.bsideup.jabel.Desugar
    public record Sample(String name, List<Object> outputs, int progress, int maxProgress, boolean active,
        boolean allowedToWork, boolean stuttering, boolean formed, String failure, long lastParallel, String shutdown,
        int maintenanceIssues, boolean outputBlocked) {

        public Sample(String name, List<Object> outputs, int progress, int maxProgress, boolean active,
            boolean allowedToWork, boolean stuttering, boolean formed, String failure, long lastParallel) {
            this(
                name,
                outputs,
                progress,
                maxProgress,
                active,
                allowedToWork,
                stuttering,
                formed,
                failure,
                lastParallel,
                null,
                0,
                false);
        }
    }

    public static List<Target> targets(ICraftingProvider provider) {
        return provider instanceof ECOProcessingTargetProvider
            ? ((ECOProcessingTargetProvider) provider).neoecoae$getProcessingTargets()
            : java.util.Collections.emptyList();
    }

    public static final class Scan {

        private final ArrayDeque<Target> pending = new ArrayDeque<>();
        private final HashSet<Target> visited = new HashSet<>();
        private final Map<String, Machine> machines = new LinkedHashMap<>();
        private final LinkedHashSet<Target> machineTargets = new LinkedHashSet<>();
        private final AEKey output;

        public Scan(Iterable<Target> roots, AEKey output) {
            for (var target : roots) pending.add(target);
            this.output = output;
        }

        public boolean complete() {
            return pending.isEmpty();
        }

        public List<Machine> machines() {
            return new ArrayList<>(machines.values());
        }

        public List<Target> machineTargets() {
            return new ArrayList<>(machineTargets);
        }

        public void addRoot(Target target) {
            if (!visited.contains(target)) pending.addLast(target);
        }

        /** One step visits one coordinate, including duplicates or unavailable positions. */
        public void step(World context) {
            if (pending.isEmpty() || context == null || context.isRemote) return;
            Target target = pending.removeFirst();
            if (!visited.add(target)) return;
            World world = context.provider.dimensionId == target.dimension() ? context
                : DimensionManager.getWorld(target.dimension());
            if (world == null || !world.getChunkProvider()
                .chunkExists(
                    target.position()
                        .getX() >> 4,
                    target.position()
                        .getZ() >> 4)) {
                machines.put(
                    location(target),
                    new Machine(
                        Component.translatable("gui.neoecoae.processing.machine"),
                        location(target),
                        "",
                        0,
                        0,
                        0,
                        State.UNAVAILABLE,
                        Component.translatable("gui.neoecoae.processing.unloaded")));
                return;
            }
            var pos = target.position();
            var tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
            if (tile == null || tile.isInvalid()) return;
            if (!NativeProcessingMachines.isCurrentController(world, pos, tile)) return;
            if (tile instanceof cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity workstation) {
                machineTargets.add(target);
                machines.put(location(target), workstation.getProcessingStatus(output));
                return;
            }
            var controllers = NativeProcessingMachines.controllers(world, pos);
            if (!controllers.isEmpty()) {
                pending.addAll(controllers);
                return;
            }
            // Hatches are not recipe controllers; only GT machines with a native output cycle are sampled.
            Sample sample = NativeGregTech.sample(tile);
            if (sample == null) {
                // The inputs went here, but no formed controller has ever claimed this block: say so instead of
                // silently showing no machine at all.
                String hatch = NativeGregTech.machineName(tile);
                machines.put(
                    location(target),
                    unresolvedDestination(
                        Component.literal(hatch != null ? hatch : blockName(world, pos)),
                        location(target),
                        hatch != null));
                return;
            }
            machineTargets.add(target);
            machines.put(
                location(target),
                sampleMachine(
                    sample,
                    Component.literal(sample.name() == null ? "GregTech" : sample.name()),
                    location(target),
                    output));
        }
    }

    static Machine sampleMachine(Sample sample, Component name, String location, AEKey output) {
        boolean matches = matches(sample.outputs(), output);
        int duration = matches ? Math.max(0, sample.maxProgress()) : 0;
        int progress = duration > 0 ? Math.max(0, sample.progress()) : 0;
        boolean active = sample.active();
        boolean paused = !active && !sample.allowedToWork();
        Component reason = reason(sample, active, paused, matches);
        State state = paused ? State.PAUSED
            : !reason.getString()
                .isEmpty() && !(active && !matches) ? State.WAITING
                    : duration > 0 && active ? State.RUNNING : State.QUEUED;
        if (state == State.QUEUED || state == State.PAUSED && duration == 0) {
            duration = 0;
            progress = 0;
        }
        long parallel = duration > 0 ? Math.max(1L, sample.lastParallel()) : 0;
        return new Machine(name, location, "", progress, duration, parallel, state, reason);
    }

    /** The most specific reason a machine is not advancing this output, in the order GT itself checks them. */
    private static Component reason(Sample sample, boolean active, boolean paused, boolean matches) {
        String shutdown = sample.shutdown() == null || sample.shutdown()
            .trim()
            .isEmpty() ? null : sample.shutdown();
        if (!sample.formed()) return Component.translatable("gui.neoecoae.processing.unformed");
        if (paused) return shutdown == null ? Component.translatable("gui.neoecoae.processing.disabled")
            : Component.translatable("gui.neoecoae.processing.shutdown", shutdown);
        if (sample.maintenanceIssues() > 0)
            return Component.translatable("gui.neoecoae.processing.maintenance", sample.maintenanceIssues());
        if (sample.stuttering()) return Component.translatable("gui.neoecoae.processing.stalled");
        if (sample.outputBlocked()) return Component.translatable("gui.neoecoae.processing.output_blocked");
        if (active) return matches ? Component.empty() : Component.translatable("gui.neoecoae.processing.other_recipe");
        if (shutdown != null) return Component.translatable("gui.neoecoae.processing.shutdown", shutdown);
        if (sample.failure() != null && !sample.failure()
            .trim()
            .isEmpty()) return Component.literal(sample.failure());
        return Component.translatable("gui.neoecoae.processing.idle");
    }

    /** A destination that holds the pushed inputs but is not itself a recipe controller. */
    static Machine unresolvedDestination(Component name, String location, boolean gregTech) {
        return new Machine(
            name,
            location,
            "",
            0,
            0,
            0,
            State.WAITING,
            Component.translatable(
                gregTech ? "gui.neoecoae.processing.no_controller" : "gui.neoecoae.processing.not_machine"));
    }

    private static String blockName(World world, cn.dancingsnow.neoecoae.platform.minecraft.BlockPos pos) {
        try {
            var block = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
            var stack = new ItemStack(block, 1, world.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ()));
            return stack.getItem() == null ? block.getLocalizedName() : stack.getDisplayName();
        } catch (RuntimeException unnamed) {
            return "?";
        }
    }

    private static boolean matches(Object contents, AEKey output) {
        if (contents instanceof ItemStack stack)
            return output instanceof AEItemKey && output.equals(AEItemKey.of(stack));
        if (contents instanceof FluidStack fluid)
            return output instanceof AEFluidKey && output.equals(AEFluidKey.of(fluid));
        if (contents instanceof Iterable<?>iterable)
            for (Object entry : iterable) if (matches(entry, output)) return true;
        return false;
    }

    private static String location(Target target) {
        var pos = target.position();
        return target.dimension() + " [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }
}
