package cn.dancingsnow.neoecoae.compat.proghatches;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPartHost;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;

/** Optional native contracts; no Programmable Hatches classes are linked when the mod is absent. */
public final class ProgrammableHatchCrafting {

    private static final String META = "reobf.proghatches.gt.metatileentity.";
    private static final String HATCH = META + "PatternDualInputHatch";
    private static final String SLAVE = META + "PatternDualInputHatchInventoryMappingSlave";
    private static final String SMALL_CIRCUIT = META + "ProgrammingCircuitProvider";
    private static final String LARGE_CIRCUIT = META + "multi.LargeProgrammingCircuitProvider";
    private static final String CIRCUIT_PATTERN = SMALL_CIRCUIT + "$CircuitProviderPatternDetial";
    private static final String INSTANT = "reobf.proghatches.eucrafting.IInstantCompletable";
    private static final String MULTIPLIER = HATCH + "$DA";
    private static final ClassValue<DispatchAccess> DISPATCH = new ClassValue<>() {

        @Override
        protected DispatchAccess computeValue(Class<?> type) {
            Method batch = null;
            Method master = null;
            boolean smallCircuit = isType(type, SMALL_CIRCUIT);
            boolean circuit = smallCircuit || isType(type, LARGE_CIRCUIT);
            if (circuit || isType(type, HATCH) || isType(type, SLAVE)) {
                batch = method(
                    type,
                    "pushPatternMulti",
                    int[].class,
                    ICraftingPatternDetails.class,
                    InventoryCrafting.class,
                    int.class);
                if (batch != null) {
                    String owner = batch.getDeclaringClass()
                        .getName();
                    if (SLAVE.equals(owner)) master = method(type, "getMaster", null);
                    else if (!HATCH.equals(owner) && !circuit) batch = null;
                }
            }
            return new DispatchAccess(
                batch,
                master,
                circuit,
                smallCircuit,
                hasInterface(type, INSTANT) ? method(type, "complete", void.class) : null,
                isType(type, LARGE_CIRCUIT) ? method(type, "instant", boolean.class) : null);
        }
    };
    private static final ClassValue<MultiplierFields> MULTIPLIERS = new ClassValue<>() {

        @Override
        protected MultiplierFields computeValue(Class<?> type) {
            if (!MULTIPLIER.equals(type.getName())) return new MultiplierFields(null, null);
            try {
                Field pattern = type.getDeclaredField("p");
                Field multiplier = type.getDeclaredField("m");
                pattern.setAccessible(true);
                multiplier.setAccessible(true);
                return new MultiplierFields(pattern, multiplier);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return new MultiplierFields(null, null);
            }
        }
    };

    private ProgrammableHatchCrafting() {}

    public static boolean isCircuitPattern(ICraftingPatternDetails pattern) {
        return isType(pattern.getClass(), CIRCUIT_PATTERN);
    }

    public static boolean canDeliverCircuitInstantly(ICraftingMedium medium, ICraftingPatternDetails pattern) {
        if (!isCircuitPattern(pattern)) return false;
        Method instant = DISPATCH.get(medium.getClass())
            .instant();
        if (instant == null) return false;
        try {
            if (!Boolean.TRUE.equals(instant.invoke(medium))) return false;
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
        return NativeGregTech.isActiveMachine(medium);
    }

    public static boolean supportsCountedBatch(ICraftingMedium medium, ICraftingPatternDetails pattern,
        boolean linearProcessing) {
        DispatchAccess access = DISPATCH.get(medium.getClass());
        if (access.batch() == null) return false;
        if (access.circuit()) {
            return isCircuitPattern(pattern)
                && (!access.smallCircuit() || pattern.getCondensedAEOutputs()[0].getStackSize() == 1);
        }
        if (!linearProcessing) return false;
        if (access.master() == null) return HATCH.equals(
            access.batch()
                .getDeclaringClass()
                .getName());
        try {
            Object master = access.master()
                .invoke(medium);
            Method masterBatch = master == null ? null
                : DISPATCH.get(master.getClass())
                    .batch();
            return masterBatch != null && HATCH.equals(
                masterBatch.getDeclaringClass()
                    .getName());
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }

    public static int pushCountedBatch(ICraftingMedium medium, ICraftingPatternDetails pattern, InventoryCrafting table,
        int offered) {
        try {
            int[] receipt = (int[]) DISPATCH.get(medium.getClass())
                .batch()
                .invoke(medium, pattern, table, offered);
            if (receipt != null && (receipt.length == 1 || receipt.length == 2 && receipt[0] == receipt[1])
                && receipt[0] >= 0
                && receipt[0] <= offered) return receipt[0];
            throw new ECOIndeterminateBatchException("Unsupported Programmable Hatches ownership receipt", null);
        } catch (ReflectiveOperationException failure) {
            throw new ECOIndeterminateBatchException("Programmable Hatches batch ownership is uncertain", failure);
        }
    }

    public static void completeAcceptedPush(ICraftingMedium medium) {
        Method complete = DISPATCH.get(medium.getClass())
            .complete();
        if (complete == null) return;
        try {
            complete.invoke(medium);
        } catch (ReflectiveOperationException ignored) {}
    }

    public static boolean requiresSingleCopy(TileEntity target, ForgeDirection face) {
        if (target == null) return false;
        if (isType(target.getClass(), "reobf.proghatches.ae.TileRequestTunnel")) return true;
        if (target instanceof IPartHost host) {
            var part = host.getPart(face);
            if (part != null && isType(part.getClass(), "reobf.proghatches.ae.part2.PartRequestTunnel")) return true;
        }
        Object meta = NativeGregTech.metaTile(target);
        return meta != null && isType(meta.getClass(), META + "BufferedDualInputHatch");
    }

    private static boolean isType(Class<?> type, String name) {
        for (; type != null; type = type.getSuperclass()) if (name.equals(type.getName())) return true;
        return false;
    }

    private static boolean hasInterface(Class<?> type, String name) {
        if (type == null) return false;
        if (name.equals(type.getName())) return true;
        for (Class<?> implemented : type.getInterfaces()) if (hasInterface(implemented, name)) return true;
        return hasInterface(type.getSuperclass(), name);
    }

    private static Method method(Class<?> type, String name, Class<?> result, Class<?>... parameters) {
        try {
            Method method = type.getMethod(name, parameters);
            return result == null || method.getReturnType() == result ? method : null;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    /** Applies ordinary processing exclusions to the pattern and every known multiplier delegate. */
    public static boolean supportsScaledProcessingPattern(ICraftingPatternDetails pattern,
        Predicate<ICraftingPatternDetails> acceptsPattern) {
        try {
            for (int depth = 0; depth < 128; depth++) {
                if (!acceptsPattern.test(pattern)) return false;
                if (!MULTIPLIER.equals(
                    pattern.getClass()
                        .getName()))
                    return true;
                MultiplierFields fields = MULTIPLIERS.get(pattern.getClass());
                if (fields.pattern() == null || fields.multiplier()
                    .getInt(pattern) <= 0) return false;
                Object delegate = fields.pattern()
                    .get(pattern);
                if (!(delegate instanceof ICraftingPatternDetails)) return false;
                pattern = (ICraftingPatternDetails) delegate;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
        return false;
    }

    @com.github.bsideup.jabel.Desugar
    private record MultiplierFields(Field pattern, Field multiplier) {}

    @com.github.bsideup.jabel.Desugar
    private record DispatchAccess(Method batch, Method master, boolean circuit, boolean smallCircuit, Method complete,
        Method instant) {}
}
