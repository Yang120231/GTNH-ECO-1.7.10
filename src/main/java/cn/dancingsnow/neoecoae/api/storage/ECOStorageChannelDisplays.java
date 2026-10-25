package cn.dancingsnow.neoecoae.api.storage;

import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Server-authored channel presentation. Aliases share a distribution segment; storage accounting is unchanged. */
public final class ECOStorageChannelDisplays {

    private static final ECOExtensionRegistry<Display> REGISTRY = new ECOExtensionRegistry<>();

    private ECOStorageChannelDisplays() {}

    @com.github.bsideup.jabel.Desugar
    public record Display(ResourceLocation channel, Set<ResourceLocation> aliases, Component name, int color,
        @Nullable ResourceLocation icon, @Nullable ResourceLocation swatch, int order) {

        public Display {
            Objects.requireNonNull(channel, "channel");
            aliases = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setCopyOf(aliases);
            name = Objects.requireNonNull(name, "name")
                .copy();
        }

        public boolean matches(ResourceLocation resource) {
            return channel.equals(resource) || aliases.contains(resource);
        }
    }

    public static void register(ResourceLocation id, int priority, Display display) {
        REGISTRY.register(id, priority, display);
    }

    public static boolean unregister(ResourceLocation id) {
        return REGISTRY.unregister(id);
    }

    public static long revision() {
        return REGISTRY.revision();
    }

    @Nullable
    public static Display resolve(@Nullable ResourceLocation resource) {
        if (resource == null) return null;
        for (var entry : REGISTRY.entries()) if (entry.extension()
            .matches(resource)) return entry.extension();
        return null;
    }
}
