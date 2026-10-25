package cn.dancingsnow.neoecoae.api.computation;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

/** Interface-based memory plus adapters for Items owned by optional mods. Descriptors are never cached per Item. */
public final class ECOComputationCells {

    private static final ECOExtensionRegistry<Adapter> REGISTRY = new ECOExtensionRegistry<>();

    private ECOComputationCells() {}

    @FunctionalInterface
    public interface Adapter {

        @Nullable
        Memory describe(ItemStack stack);
    }

    @com.github.bsideup.jabel.Desugar
    public record Memory(StorageTierDescriptor tier, long bytes) {

        public Memory {
            java.util.Objects.requireNonNull(tier, "tier");
            if (bytes < 0) throw new IllegalArgumentException("Negative computation bytes");
        }
    }

    public static void register(ResourceLocation id, int priority, Adapter adapter) {
        REGISTRY.register(id, priority, adapter);
    }

    public static boolean unregister(ResourceLocation id) {
        return REGISTRY.unregister(id);
    }

    public static long revision() {
        return REGISTRY.revision();
    }

    @Nullable
    public static Memory describe(@Nullable ItemStack stack) {
        if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) return null;
        for (var entry : REGISTRY.entries()) {
            var memory = entry.extension()
                .describe(stack);
            if (memory != null) return memory;
        }
        return stack.getItem() instanceof IECOComputationCellItem item
            ? new Memory(item.getTier(stack), item.getComputationBytes(stack))
            : null;
    }
}
