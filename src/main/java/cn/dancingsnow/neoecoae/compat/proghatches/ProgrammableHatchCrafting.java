package cn.dancingsnow.neoecoae.compat.proghatches;

import java.lang.reflect.Field;
import java.util.function.Predicate;

import net.minecraft.inventory.InventoryCrafting;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;

/** Optional native contracts; no Programmable Hatches classes are linked when the mod is absent. */
public final class ProgrammableHatchCrafting {

    private static final String HATCH = "reobf.proghatches.gt.metatileentity.PatternDualInputHatch";
    private static final String MULTIPLIER = HATCH + "$DA";
    private static final ClassValue<Boolean> SCALED_HATCHES = new ClassValue<>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                if (!HATCH.equals(parent.getName())) continue;
                try {
                    // Keep the existing exclusion for programmable variants with special per-push semantics.
                    return HATCH.equals(
                        type.getMethod("pushPattern", ICraftingPatternDetails.class, InventoryCrafting.class)
                            .getDeclaringClass()
                            .getName())
                        && HATCH.equals(
                            type.getMethod("isBusy")
                                .getDeclaringClass()
                                .getName());
                } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                    return false;
                }
            }
            return true;
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

    public static boolean supportsScaledProcessing(ICraftingMedium medium) {
        return SCALED_HATCHES.get(medium.getClass());
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
}
