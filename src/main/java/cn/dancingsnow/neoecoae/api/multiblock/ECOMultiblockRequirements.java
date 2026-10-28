package cn.dancingsnow.neoecoae.api.multiblock;

import java.util.Objects;
import java.util.Set;

import net.minecraft.block.Block;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Property;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.minecraft.WorldView;

/** Shared acceptance rules for placement, preview and formation. Predicates must be pure and never load chunks. */
public final class ECOMultiblockRequirements {

    private static final Set<String> DERIVED_PROPERTIES = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(
        "formed",
        "working",
        "mirrored",
        "invisible",
        "classic_visible",
        "has_cell",
        "lit",
        // Controllers derive these from the installed switch; "network_switch" is the actual property name.
        "network_switch",
        "normal_network_switch",
        "high_energy_network_switch");
    private static final ECOExtensionRegistry<Provider> REGISTRY = new ECOExtensionRegistry<>();

    private ECOMultiblockRequirements() {}

    @com.github.bsideup.jabel.Desugar
    public record Context(@Nullable WorldView level, BlockPos position, BlockState expected, BlockState actual) {}

    @FunctionalInterface
    public interface Provider {

        @Nullable
        Boolean accepts(Context context);
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

    public static void registerAlternatives(ResourceLocation id, int priority, Block expected,
        Set<Block> alternatives) {
        Set<Block> copy = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(alternatives);
        register(
            id,
            priority,
            context -> context.expected()
                .is(expected)
                && copy.contains(
                    context.actual()
                        .getBlock()) ? matchingProperties(context.expected(), context.actual()) : null);
    }

    public static boolean accepts(Context context, boolean fallback) {
        for (var entry : REGISTRY.entries()) {
            Boolean accepted = entry.extension()
                .accepts(context);
            if (accepted != null) return accepted;
        }
        return fallback;
    }

    public static boolean matchingProperties(BlockState expected, BlockState actual) {
        for (Property<?> property : expected.getProperties()) {
            if (DERIVED_PROPERTIES.contains(property.getName())) continue;
            if (actual.hasProperty(property) && !Objects.equals(expected.getValue(property), actual.getValue(property)))
                return false;
        }
        return true;
    }
}
