package cn.dancingsnow.neoecoae.platform.minecraft;

public final class ResourceLocation extends net.minecraft.util.ResourceLocation {

    private ResourceLocation(String namespace, String path) {
        super(namespace, path);
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        // 1.7.10 registry names are not modern resource paths: spaces, punctuation,
        // non-ASCII characters and further colons can all occur after the namespace.
        // Preserve the exact name (including case) for item lookup, persistence and
        // migration transaction IDs. The native two-argument constructor does this too.
        if (namespace == null || namespace.isEmpty() || namespace.indexOf(':') >= 0 || path == null || path.isEmpty())
            throw new IllegalArgumentException("Invalid resource identifier: " + namespace + ":" + path);
        return new ResourceLocation(namespace, path);
    }

    public static ResourceLocation parse(String value) {
        if (value == null) throw new IllegalArgumentException("Invalid resource identifier: null");
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
