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
     * @param outputs the cycle's pending ItemStack and FluidStack outputs
     * @param formed  false for an unformed multiblock controller
     * @param failure the last recipe check's message when it failed, otherwise null
     */
    @com.github.bsideup.jabel.Desugar
    public record Sample(String name, List<Object> outputs, int progress, int maxProgress, boolean active,
        boolean allowedToWork, boolean stuttering, boolean formed, String failure, long lastParallel) {}

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
            if (sample == null) return;
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
        Component reason = Component.empty();
        if (!sample.formed()) reason = Component.translatable("gui.neoecoae.processing.unformed");
        else if (sample.stuttering()) reason = Component.translatable("gui.neoecoae.processing.stalled");
        else if (!active && sample.failure() != null) reason = Component.literal(sample.failure());
        State state = paused ? State.PAUSED
            : !reason.getString()
                .isEmpty() ? State.WAITING : duration > 0 && active ? State.RUNNING : State.QUEUED;
        if (state == State.QUEUED) {
            duration = 0;
            progress = 0;
        }
        long parallel = duration > 0 ? Math.max(1L, sample.lastParallel()) : 0;
        return new Machine(name, location, "", progress, duration, parallel, state, reason);
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
