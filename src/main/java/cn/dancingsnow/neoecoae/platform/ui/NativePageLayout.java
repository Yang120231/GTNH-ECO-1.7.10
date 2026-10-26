package cn.dancingsnow.neoecoae.platform.ui;

import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.Widget;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Source JSON owns node geometry. Unknown custom nodes must be handled explicitly by each native page. */
public final class NativePageLayout {

    private static final Map<String, int[]> IMAGE_SIZES = new HashMap<>();
    private final JsonObject root;

    public NativePageLayout(String path) {
        var stream = NativePageLayout.class.getResourceAsStream("/assets/neoecoae/screens/" + path + ".json");
        if (stream == null) throw new IllegalArgumentException("Missing source page: " + path);
        try (var input = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(input)
                .getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read source page: " + path, error);
        }
    }

    public JsonObject root() {
        return root;
    }

    public int width() {
        return root.getAsJsonObject("layout")
            .get("width")
            .getAsInt();
    }

    public int height() {
        return root.getAsJsonObject("layout")
            .get("height")
            .getAsInt();
    }

    public void place(Widget<?> widget, JsonObject node) {
        JsonObject box = node.getAsJsonObject("layout");
        int width = box.get("width")
            .getAsInt(),
            height = box.get("height")
                .getAsInt();
        int x = box.has("left") ? box.get("left")
            .getAsInt()
            : this.width() - width
                - box.get("right")
                    .getAsInt();
        int y = box.has("top") ? box.get("top")
            .getAsInt()
            : this.height() - height
                - box.get("bottom")
                    .getAsInt();
        widget.pos(x, y)
            .size(width, height);
    }

    public static UITexture texture(String id, int x, int y, int width, int height) {
        return texture(id, x, y, width, height, 0, 0);
    }

    public static UITexture texture(String id, int x, int y, int width, int height, int borderX, int borderY) {
        int[] image = IMAGE_SIZES.computeIfAbsent(id, NativePageLayout::imageSize);
        return UITexture.builder()
            .location(id)
            .imageSize(image[0], image[1])
            .subAreaXYWH(x, y, width, height)
            .adaptable(borderX, borderY)
            .build();
    }

    private static int[] imageSize(String id) {
        net.minecraft.util.ResourceLocation location = new net.minecraft.util.ResourceLocation(id);
        String path = "/assets/" + location.getResourceDomain() + "/" + location.getResourcePath();
        var stream = NativePageLayout.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalArgumentException("Missing source UI image: " + id);
        try (var input = new DataInputStream(stream)) {
            if (input.readLong() != 0x89504e470d0a1a0aL) throw new IllegalArgumentException("Not a PNG: " + id);
            input.readInt();
            if (input.readInt() != 0x49484452) throw new IllegalArgumentException("Missing PNG header: " + id);
            return new int[] { input.readInt(), input.readInt() };
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read source image header: " + id, error);
        }
    }
}
