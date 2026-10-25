package cn.dancingsnow.neoecoae.api.power;

import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

/** Idle power in AE/t, including the installed cell drain. Register on both logical sides during setup. */
public final class ECOPowerProfiles {

    private static final ECOExtensionRegistry<Provider> REGISTRY = new ECOExtensionRegistry<>();

    private ECOPowerProfiles() {}

    @FunctionalInterface
    public interface Provider {

        /** Null delegates to the next provider. The result is the complete idle draw, not a multiplier. */
        @Nullable
        Double idlePower(Context context);
    }

    @com.github.bsideup.jabel.Desugar
    public record Context(TileEntity host, BlockState state, @Nullable StorageTierDescriptor tier, ECOPowerRole role,
        double basePower, double cellDrain) {

        public double defaultPower() {
            return basePower + cellDrain;
        }
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        REGISTRY.register(id, priority, provider);
    }

    public static boolean unregister(ResourceLocation id) {
        return REGISTRY.unregister(id);
    }

    public static long revision() {
        return REGISTRY.revision();
    }

    public static double resolve(Context context) {
        for (var entry : REGISTRY.entries()) {
            Double power = entry.extension()
                .idlePower(context);
            if (power != null) return validate(power);
        }
        return validate(context.defaultPower());
    }

    private static double validate(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid idle power: " + value);
        return value;
    }
}
