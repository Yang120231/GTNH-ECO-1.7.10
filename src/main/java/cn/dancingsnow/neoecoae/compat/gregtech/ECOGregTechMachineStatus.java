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
     * @param lastParallel      the current recipe's actual parallel, or zero when it is unknown
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
        private final ArrayDeque<Target> sharedPending = new ArrayDeque<>();
        private final HashSet<Target> sharedVisited = new HashSet<>();
        private final LinkedHashSet<Target> sharedMachines = new LinkedHashSet<>();
        private final Map<String, Machine> machines = new LinkedHashMap<>();
        private final LinkedHashSet<Target> machineTargets = new LinkedHashSet<>();
        private final AEKey output;

        public Scan(Iterable<Target> roots, AEKey output) {
            for (var target : roots) pending.add(target);
            this.output = output;
        }

        public boolean complete() {
            return pending.isEmpty() && sharedPending.isEmpty();
        }

        public List<Machine> machines() {
            return new ArrayList<>(machines.values());
        }

        public List<Target> machineTargets() {
            return new ArrayList<>(machineTargets);
        }

        public int sharedMachineCount() {
            if (sharedMachines.isEmpty()) return 0;
            var all = new LinkedHashSet<>(machineTargets);
            all.addAll(sharedMachines);
            return all.size();
        }

        public void addRoot(Target target) {
            if (!visited.contains(target)) pending.addLast(target);
        }

        /** One step visits one coordinate, including duplicates or unavailable positions. */
        public void step(World context) {
            if (complete() || context == null || context.isRemote) return;
            boolean sharedOnly = pending.isEmpty();
            Target target = sharedOnly ? sharedPending.removeFirst() : pending.removeFirst();
            if (!(sharedOnly ? sharedVisited : visited).add(target)) return;
            World world = context.provider.dimensionId == target.dimension() ? context
                : DimensionManager.getWorld(target.dimension());
            if (world == null || !world.getChunkProvider()
                .chunkExists(
                    target.position()
                        .getX() >> 4,
                    target.position()
                        .getZ() >> 4)) {
                if (sharedOnly) return;
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
            if (sharedOnly) {
                sharedMachines.addAll(NativeProcessingMachines.controllers(world, pos));
                return;
            }
            for (var proxy : NativeGregTech.craftingInputProxies(tile)) {
                if (proxy.getWorldObj() != world) continue;
                sharedPending.addLast(
                    new Target(
                        target.dimension(),
                        new cn.dancingsnow.neoecoae.platform.minecraft.BlockPos(
                            proxy.xCoord,
                            proxy.yCoord,
                            proxy.zCoord)));
            }
            if (!NativeProcessingMachines.isCurrentController(world, pos, tile)) return;
            // ECO's own multiblock inputs are not GT hatches and therefore have no GT membership links.
            var workstationCluster = tile instanceof cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity provider
                ? provider.getCluster()
                : tile instanceof cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity hatch
                    ? hatch.getCluster()
                    : null;
            if (workstationCluster != null && workstationCluster.getController() != null) {
                pending.addFirst(
                    new Target(
                        target.dimension(),
                        workstationCluster.getController()
                            .getBlockPos()));
                return;
            }
            if (tile instanceof cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity workstation) {
                machineTargets.add(target);
                machines.put(location(target), workstation.getProcessingStatus(output));
                return;
            }
            if (tile instanceof cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity worker) {
                machineTargets.add(target);
                machines.put(location(target), worker.getProcessingStatus(output));
                return;
            }
            var controllers = NativeProcessingMachines.controllers(world, pos);
            if (!controllers.isEmpty()) {
                // Resolve the machine behind this accepted input before visiting every other input destination.
                for (int i = controllers.size() - 1; i >= 0; i--) pending.addFirst(controllers.get(i));
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
        long parallel = duration > 0 ? Math.max(0L, sample.lastParallel()) : 0;
        long cycleOutput = 0L;
        if (duration > 0) for (Object entry : sample.outputs()) {
            if (entry instanceof ItemStack stack && matches(stack, output))
                cycleOutput = cn.dancingsnow.neoecoae.crafting.amount.NEMath
                    .saturatingAdd(cycleOutput, Math.max(0, stack.stackSize));
            else if (entry instanceof FluidStack fluid && matches(fluid, output))
                cycleOutput = cn.dancingsnow.neoecoae.crafting.amount.NEMath
                    .saturatingAdd(cycleOutput, Math.max(0, fluid.amount));
        }
        return new Machine(name, location, "", progress, duration, parallel, cycleOutput, state, reason);
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
