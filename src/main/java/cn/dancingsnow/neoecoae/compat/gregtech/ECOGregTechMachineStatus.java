package cn.dancingsnow.neoecoae.compat.gregtech;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
import cn.dancingsnow.neoecoae.platform.gt.NativeProcessingMachines;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;

/** Optional GTNH status reader. No GT linkage, inventory simulations, recipe retries or chunk loads. */
public final class ECOGregTechMachineStatus {

    private static final ClassValue<Map<String, Optional<Member>>> MEMBERS = new ClassValue<Map<String, Optional<Member>>>() {
        @Override protected Map<String, Optional<Member>> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };

    private ECOGregTechMachineStatus() {}

    public static List<Target> targets(ICraftingProvider provider) {
        return provider instanceof ECOProcessingTargetProvider
            ? ((ECOProcessingTargetProvider) provider).neoecoae$getProcessingTargets() : java.util.Collections.emptyList();
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

        public boolean complete() { return pending.isEmpty(); }
        public List<Machine> machines() { return new ArrayList<>(machines.values()); }
        public List<Target> machineTargets() { return new ArrayList<>(machineTargets); }
        public void addRoot(Target target) { if (!visited.contains(target)) pending.addLast(target); }

        /** One step visits one coordinate, including duplicates or unavailable positions. */
        public void step(World context) {
            if (pending.isEmpty() || context == null || context.isRemote) return;
            Target target = pending.removeFirst();
            if (!visited.add(target)) return;
            World world = context.provider.dimensionId == target.dimension() ? context : DimensionManager.getWorld(target.dimension());
            if (world == null || !world.getChunkProvider().chunkExists(target.position().getX() >> 4, target.position().getZ() >> 4)) {
                machines.put(location(target), new Machine(Component.translatable("gui.neoecoae.processing.machine"),
                    location(target), "", 0, 0, 0, State.UNAVAILABLE, Component.translatable("gui.neoecoae.processing.unloaded")));
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
            if (!controllers.isEmpty()) { pending.addAll(controllers); return; }
            Object machine = value(tile, "getMetaTileEntity");
            if (machine == null) return;
            // Hatches are not recipe controllers. Recognize the native cycle fields rather than neighboring blocks.
            if (value(machine, "mOutputItems", "mOutputFluids", "mOutputFluid") == null) return;
            machineTargets.add(target);
            Object name = value(machine, "getLocalName", "getMetaName");
            machines.put(location(target), sampleMachine(tile, machine, Component.literal(name == null ? "GregTech" : name.toString()), location(target), output));
        }
    }

    static Machine sampleMachine(Object tile, Object machine, Component name, String location, AEKey output) {
        boolean matches = matches(value(machine, "mOutputItems"), output) || matches(value(machine, "mOutputFluids"), output)
            || matches(value(machine, "mOutputFluid"), output);
        int duration = matches ? positiveInt(value(tile, "getMaxProgress")) : 0;
        int progress = duration > 0 ? positiveInt(value(tile, "getProgress")) : 0;
        boolean active = Boolean.TRUE.equals(value(tile, "isActive"));
        boolean paused = !active && Boolean.FALSE.equals(value(tile, "isAllowedToWork"));
        boolean stalled = Boolean.TRUE.equals(value(machine, "mStuttering"));
        Component reason = Component.empty();
        if (Boolean.FALSE.equals(value(machine, "mMachine"))) reason = Component.translatable("gui.neoecoae.processing.unformed");
        else if (stalled) reason = Component.translatable("gui.neoecoae.processing.stalled");
        else if (!active) {
            Object result = value(machine, "checkRecipeResult");
            if (Boolean.FALSE.equals(value(result, "wasSuccessful"))) {
                Object text = value(result, "getDisplayString");
                if (text != null) reason = Component.literal(text.toString());
            }
        }
        State state = paused ? State.PAUSED : !reason.getString().isEmpty() ? State.WAITING
            : duration > 0 && active ? State.RUNNING : State.QUEUED;
        if (state == State.QUEUED) { duration = 0; progress = 0; }
        long parallel = duration > 0 ? Math.max(1L, positiveLong(value(machine, "lastParallel"))) : 0;
        return new Machine(name, location, "", progress, duration, parallel, state, reason);
    }

    private static boolean matches(Object contents, AEKey output) {
        if (contents instanceof ItemStack stack) return output instanceof AEItemKey && output.equals(AEItemKey.of(stack));
        if (contents instanceof FluidStack fluid) return output instanceof AEFluidKey && output.equals(AEFluidKey.of(fluid));
        if (contents instanceof Object[] array) for (Object entry : array) if (matches(entry, output)) return true;
        return false;
    }

    private static String location(Target target) {
        var pos = target.position();
        return target.dimension() + " [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }

    private static int positiveInt(Object value) { return (int) Math.min(Integer.MAX_VALUE, positiveLong(value)); }
    private static long positiveLong(Object value) { return value instanceof Number ? Math.max(0L, ((Number) value).longValue()) : 0L; }

    public static Object value(Object object, String... names) {
        if (object == null) return null;
        try {
            var members = MEMBERS.get(object.getClass());
            for (String name : names) {
                var member = members.computeIfAbsent(name, key -> member(object.getClass(), key));
                if (member.isPresent()) {
                    Object result = member.get().get(object);
                    if (result != null) return result;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError incompatible) {
            // Observation must not affect crafting or custody of already accepted batches.
        }
        return null;
    }

    private static Optional<Member> member(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return Optional.of(object -> method.invoke(object));
        } catch (NoSuchMethodException ignored) {}
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return Optional.of(object -> field.get(object));
            } catch (NoSuchFieldException ignored) {}
        }
        return Optional.empty();
    }

    @FunctionalInterface
    private interface Member { Object get(Object object) throws ReflectiveOperationException; }
}
