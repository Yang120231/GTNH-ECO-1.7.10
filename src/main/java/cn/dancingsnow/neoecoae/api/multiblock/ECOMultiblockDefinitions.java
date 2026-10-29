package cn.dancingsnow.neoecoae.api.multiblock;

import java.util.function.Supplier;

import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

/**
 * Defines the geometry StructureLib's hologram projector builds and BlockRenderer6343 previews in NEI; pair custom
 * formation rules with NEBlockEntity.registerCalculatorFactory. Providers are resolved on both sides, including for
 * hosts in NEI preview worlds, so they must not assume a server world.
 */
public final class ECOMultiblockDefinitions {

    private static final ECOExtensionRegistry<Provider> REGISTRY = new ECOExtensionRegistry<>();

    private ECOMultiblockDefinitions() {}

    @FunctionalInterface
    public interface Provider {

        @Nullable
        MultiBlockDefinition definition(Context context);
    }

    @com.github.bsideup.jabel.Desugar
    public record Context(TileEntity host, ECOMachineKind kind, StorageTierDescriptor tier) {}

    public static void register(ResourceLocation id, int priority, Provider provider) {
        REGISTRY.register(id, priority, provider);
    }

    public static boolean unregister(ResourceLocation id) {
        return REGISTRY.unregister(id);
    }

    public static long revision() {
        return REGISTRY.revision();
    }

    @Nullable
    public static MultiBlockDefinition resolve(TileEntity host, ECOMachineKind kind, StorageTierDescriptor tier,
        Supplier<MultiBlockDefinition> fallback) {
        var context = new Context(host, kind, tier);
        for (var entry : REGISTRY.entries()) {
            var definition = entry.extension()
                .definition(context);
            if (definition != null) return definition;
        }
        var definition = tier.getBuildDefinition(kind);
        return definition != null ? definition : fallback.get();
    }
}
