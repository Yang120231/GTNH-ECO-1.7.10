package cn.dancingsnow.neoecoae.platform.gt;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;

import cn.dancingsnow.neoecoae.compat.gregtech.ECOGregTechMachineStatus;
import cn.dancingsnow.neoecoae.compat.gtnr.upload.CrossRecipePatternUpload;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.multi.MTEIntegratedOreFactory;
import gregtech.common.tileentities.machines.multi.MTEIntegratedOreFactoryLegacy;

/** Native API linkage is isolated from the no-GT load path. */
final class GregTechMachineAccess {

    private static final Field PROCESSING_LOGIC = nativeField("processingLogic");
    private static final Map<MTEMultiBlockBase, CycleParallel> CYCLE_PARALLELS = new WeakHashMap<>();

    private GregTechMachineAccess() {}

    static void verifyApi() {
        try {
            IGregTechTileEntity.class.getMethod("getMetaTileEntity");
            MTEBasicMachine.class.getField("mProgresstime");
            MTEBasicMachine.class.getField("mMaxProgresstime");
            MTEBasicMachine.class.getField("mEUt");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("GregTech native machine API is incompatible", failure);
        }
    }

    /** A recipe controller's current cycle, or null for hatches and other non-processing tiles. */
    static ECOGregTechMachineStatus.Sample sample(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity)) return null;
        IGregTechTileEntity host = (IGregTechTileEntity) tile;
        IMetaTileEntity machine = host.getMetaTileEntity();
        String name = machine == null ? null : machine.getLocalName();
        if (name == null && machine != null) name = machine.getMetaName();
        if (machine instanceof MTEMultiBlockBase) {
            MTEMultiBlockBase multi = (MTEMultiBlockBase) machine;
            List<Object> outputs = new ArrayList<>();
            if (multi.mOutputItems != null) outputs.addAll(Arrays.asList(multi.mOutputItems));
            if (multi.mOutputFluids != null) outputs.addAll(Arrays.asList(multi.mOutputFluids));
            var result = multi.getCheckRecipeResult();
            return new ECOGregTechMachineStatus.Sample(
                name,
                outputs,
                host.getProgress(),
                host.getMaxProgress(),
                host.isActive(),
                host.isAllowedToWork(),
                false,
                multi.mMachine,
                result == null || result.wasSuccessful() ? null : result.getDisplayString(),
                currentParallels(multi),
                shutdown(host),
                maintenanceIssues(multi),
                false);
        }
        if (machine instanceof MTEBasicMachine) {
            MTEBasicMachine basic = (MTEBasicMachine) machine;
            List<Object> outputs = new ArrayList<>(Arrays.asList(basic.mOutputItems));
            if (basic.mOutputFluid != null) outputs.add(basic.mOutputFluid);
            return new ECOGregTechMachineStatus.Sample(
                name,
                outputs,
                host.getProgress(),
                host.getMaxProgress(),
                host.isActive(),
                host.isAllowedToWork(),
                basic.mStuttering,
                true,
                null,
                1,
                shutdown(host),
                0,
                basic.mOutputBlocked > 0);
        }
        return null;
    }

    /** The scanner can clear ProcessingLogic while a recipe runs; keep only that accepted cycle's value. */
    private static synchronized long currentParallels(MTEMultiBlockBase multi) {
        if (multi.mMaxProgresstime <= 0) {
            CYCLE_PARALLELS.remove(multi);
            return 0;
        }
        long parallel = processingParallels(multi);
        if (parallel > 0) {
            rememberParallels(multi, parallel);
            return parallel;
        }
        CycleParallel cycle = CYCLE_PARALLELS.get(multi);
        if (cycle != null && cycle.matches(multi)) {
            cycle.progress = Math.max(0, multi.mProgresstime);
            return cycle.parallel;
        }
        CYCLE_PARALLELS.remove(multi);
        return 0;
    }

    /** Every native recipe check begins a new cycle, even when its duration and outputs are unchanged. */
    static synchronized void recipeChecked(Object machine, boolean successful) {
        if (!(machine instanceof MTEMultiBlockBase multi)) return;
        CYCLE_PARALLELS.remove(multi);
        if (successful && multi.mMaxProgresstime > 0) rememberParallels(multi, processingParallels(multi));
    }

    /** GT's lastParallel NBT field describes the restored cycle only, not subsequent recipe checks. */
    static synchronized void recipeLoaded(Object machine) {
        if (!(machine instanceof MTEMultiBlockBase multi)) return;
        CYCLE_PARALLELS.remove(multi);
        if (multi.mMaxProgresstime <= 0 || PROCESSING_LOGIC == null) return;
        try {
            if (PROCESSING_LOGIC.get(multi) != null) rememberParallels(multi, multi.lastParallel);
        } catch (IllegalAccessException | RuntimeException unavailable) {
            // An unavailable native value stays unknown.
        }
    }

    private static long processingParallels(MTEMultiBlockBase multi) {
        if (PROCESSING_LOGIC == null) return 0;
        try {
            // These custom GT controllers calculate the accepted parallel directly, outside ProcessingLogic.
            if (multi instanceof MTEIntegratedOreFactory || multi instanceof MTEIntegratedOreFactoryLegacy)
                return Math.max(0, multi.lastParallel);
            ProcessingLogic logic = (ProcessingLogic) PROCESSING_LOGIC.get(multi);
            return logic == null ? 0 : Math.max(0, logic.getCurrentParallels());
        } catch (IllegalAccessException | RuntimeException | LinkageError unavailable) {
            return 0;
        }
    }

    private static void rememberParallels(MTEMultiBlockBase multi, long parallel) {
        if (parallel > 0) CYCLE_PARALLELS.put(multi, new CycleParallel(multi, parallel));
    }

    private static final class CycleParallel {

        final long parallel;
        final int duration;
        final Object itemOutputs;
        final Object fluidOutputs;
        int progress;

        CycleParallel(MTEMultiBlockBase multi, long parallel) {
            this.parallel = parallel;
            duration = multi.mMaxProgresstime;
            itemOutputs = multi.mOutputItems;
            fluidOutputs = multi.mOutputFluids;
            progress = Math.max(0, multi.mProgresstime);
        }

        boolean matches(MTEMultiBlockBase multi) {
            return duration == multi.mMaxProgresstime && itemOutputs == multi.mOutputItems
                && fluidOutputs == multi.mOutputFluids
                && progress <= Math.max(0, multi.mProgresstime);
        }
    }

    static boolean openGui(TileEntity tile, net.minecraft.entity.player.EntityPlayer player) {
        if (!(tile instanceof IGregTechTileEntity)) return false;
        IMetaTileEntity machine = ((IGregTechTileEntity) tile).getMetaTileEntity();
        if (!(machine instanceof gregtech.api.metatileentity.CommonMetaTileEntity common)) return false;
        common.openGui(player);
        return true;
    }

    static IMetaTileEntity metaTile(TileEntity tile) {
        return tile instanceof IGregTechTileEntity host ? host.getMetaTileEntity() : null;
    }

    static String machineName(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity)) return null;
        IMetaTileEntity machine = ((IGregTechTileEntity) tile).getMetaTileEntity();
        if (machine == null) return null;
        String name = machine.getLocalName();
        return name == null ? machine.getMetaName() : name;
    }

    /**
     * GT's own last shutdown reason (power loss, out of fluid, structure broken...), when the machine was shut down.
     */
    private static String shutdown(IGregTechTileEntity host) {
        try {
            if (!host.wasShutdown()) return null;
            var reason = host.getLastShutDownReason();
            return reason == null ? null : reason.getDisplayString();
        } catch (RuntimeException | LinkageError olderGregTech) {
            return null;
        }
    }

    /** Unfixed maintenance problems; a controller that skips maintenance reports none. */
    private static int maintenanceIssues(MTEMultiBlockBase multi) {
        try {
            if (!multi.shouldCheckMaintenance()) return 0;
            return Math.max(0, multi.getIdealStatus() - multi.getRepairStatus());
        } catch (RuntimeException | LinkageError olderGregTech) {
            return 0;
        }
    }

    /**
     * Hatch lists resolved reflectively: their visibility differs between GT builds (mSmartInputHatches is private in
     * GTNH 2.9), and a direct field reference fails with IllegalAccessError inside the controller tick.
     */
    private static final List<Field> INPUT_LISTS = inputLists(
        "mInputBusses",
        "mInputHatches",
        "mDualInputHatches",
        "mSmartInputHatches");

    private static List<Field> inputLists(String... names) {
        List<Field> fields = new ArrayList<>();
        for (String name : names) {
            Field field = nativeField(name);
            if (field != null) fields.add(field);
        }
        return Collections.unmodifiableList(fields);
    }

    private static Field nativeField(String name) {
        try {
            Field field = MTEMultiBlockBase.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    /** Input hatch and bus tiles of a formed multiblock controller. */
    static List<TileEntity> inputs(Object machine) {
        if (!(machine instanceof MTEMultiBlockBase)) return Collections.emptyList();
        List<TileEntity> tiles = new ArrayList<>();
        for (Field field : INPUT_LISTS) {
            Object value;
            try {
                value = field.get(machine);
            } catch (IllegalAccessException ignored) {
                continue;
            }
            if (!(value instanceof List)) continue;
            List<?> hatches = (List<?>) value;
            for (Object hatch : hatches) {
                TileEntity base = hostTile(hatch);
                if (base != null) tiles.add(base);
                // A crafting input proxy feeds from its master buffer, which is where patterns are pushed.
                Object masterHatch = craftingInputMaster(hatch);
                if (masterHatch == null && "mDualInputHatches".equals(field.getName()))
                    masterHatch = CrossRecipePatternUpload.mirrorMaster(hatch);
                TileEntity master = hostTile(masterHatch);
                if (master != null) tiles.add(master);
            }
        }
        return tiles;
    }

    static TileEntity hostTile(Object machine) {
        if (!(machine instanceof IMetaTileEntity)) return null;
        IGregTechTileEntity base = ((IMetaTileEntity) machine).getBaseMetaTileEntity();
        return base instanceof TileEntity ? (TileEntity) base : null;
    }

    private static final Field HATCH_WATCHERS = hatchWatchers();

    private static Field hatchWatchers() {
        try {
            Field field = MTEHatch.class.getDeclaredField("watchers");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    /** GTNL registers its mirrors as hatch watchers rather than in GT's native proxy list. */
    static List<TileEntity> craftingInputProxies(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity host)) return Collections.emptyList();
        Object machine = host.getMetaTileEntity();
        if (!(machine instanceof MTEHatchCraftingInputME)
            && !isType(machine, "com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME"))
            return Collections.emptyList();
        var proxies = new java.util.LinkedHashSet<TileEntity>();
        if (machine instanceof MTEHatchCraftingInputME buffer) {
            for (Object proxy : buffer.getProxyHatches()) {
                TileEntity base = hostTile(proxy);
                if (base != null && !base.isInvalid()) proxies.add(base);
            }
        }
        if (HATCH_WATCHERS != null) {
            try {
                Object value = HATCH_WATCHERS.get(machine);
                if (value instanceof Iterable<?>watchers) for (Object watcher : watchers) {
                    if (!isType(watcher, "com.science.gtnl.common.machine.hatch.SuperCraftingInputProxy")) continue;
                    TileEntity base = hostTile(watcher);
                    if (base != null && !base.isInvalid()) proxies.add(base);
                }
            } catch (IllegalAccessException | RuntimeException | LinkageError unavailable) {
                // Optional GTNL observation must not interrupt native controller sampling.
            }
        }
        return new ArrayList<>(proxies);
    }

    private static boolean isType(Object value, String name) {
        if (value != null) for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
            if (name.equals(type.getName())) return true;
        return false;
    }

    /** MTEHatchCraftingInputSlave#getMaster, resolved reflectively since the proxy class varies between GT builds. */
    private static final java.lang.reflect.Method CRAFTING_INPUT_MASTER = craftingInputMaster();

    private static java.lang.reflect.Method craftingInputMaster() {
        try {
            return Class.forName("gregtech.common.tileentities.machines.MTEHatchCraftingInputSlave")
                .getMethod("getMaster");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            return null;
        }
    }

    private static Object craftingInputMaster(Object hatch) {
        if (CRAFTING_INPUT_MASTER == null || !CRAFTING_INPUT_MASTER.getDeclaringClass()
            .isInstance(hatch)) return null;
        try {
            return CRAFTING_INPUT_MASTER.invoke(hatch);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    static NativeGregTech.MachineSnapshot inspect(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity)) return null;
        IGregTechTileEntity host = (IGregTechTileEntity) tile;
        var machine = host.getMetaTileEntity();
        if (!(machine instanceof MTEBasicMachine)) return null;
        MTEBasicMachine basic = (MTEBasicMachine) machine;
        return new NativeGregTech.MachineSnapshot(
            host.getMetaTileID(),
            machine.getClass()
                .getName(),
            host.isActive(),
            host.getStoredEU(),
            host.getEUCapacity(),
            basic.mProgresstime,
            basic.mMaxProgresstime,
            basic.mEUt);
    }
}
