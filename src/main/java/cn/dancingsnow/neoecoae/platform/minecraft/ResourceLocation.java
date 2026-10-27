package cn.dancingsnow.neoecoae.platform.minecraft;

public final class ResourceLocation extends net.minecraft.util.ResourceLocation {

    private ResourceLocation(String namespace, String path) {
        super(namespace, path);
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        // 1.7.10 registries predate the modern lowercase-only resource-location
        // contract. Several legacy mods (including the pinned GTNH AE2 build)
        // expose item names such as "ItemBlankPattern". Keep those names
        // round-trippable so persisted AE keys can be written and read back.
        if (!namespace.matches("[A-Za-z0-9_.-]+") || !path.matches("[A-Za-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid resource identifier");
        return new ResourceLocation(namespace, path);
    }

    public static ResourceLocation parse(String value) {
        int colon = value.indexOf(':');
        return fromNamespaceAndPath(
            colon < 0 ? "minecraft" : value.substring(0, colon),
            colon < 0 ? value : value.substring(colon + 1));
    }

    public static ResourceLocation tryParse(String value) {
        try {
            return parse(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static ResourceLocation withDefaultNamespace(String value) {
        return fromNamespaceAndPath("minecraft", value);
    }

    public String getNamespace() {
        return getResourceDomain();
    }

    public String getPath() {
        return getResourcePath();
    }
}
