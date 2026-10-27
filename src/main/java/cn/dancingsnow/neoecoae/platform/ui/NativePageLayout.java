package cn.dancingsnow.neoecoae.platform.ui;

import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
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
            JsonObject document = new JsonParser().parse(input)
                .getAsJsonObject();
            root = document.has("root") ? document.getAsJsonObject("root") : document;
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
        int[] containing = containing(root, node, width(), height());
        place(
            widget,
            node,
            containing == null ? width() : containing[0],
            containing == null ? height() : containing[1]);
    }

    private static int[] containing(JsonObject parent, JsonObject wanted, int width, int height) {
        if (!parent.has("children")) return null;
        for (var value : parent.getAsJsonArray("children")) {
            JsonObject child = value.getAsJsonObject();
            if (child == wanted) return new int[] { width, height };
            JsonObject box = child.has("layout") ? child.getAsJsonObject("layout") : new JsonObject();
            int[] found = containing(
                child,
                wanted,
                dimension(box, "width", width, width),
                dimension(box, "height", height, height));
            if (found != null) return found;
        }
        return null;
    }

    @com.github.bsideup.jabel.Desugar
    public record Bounds(int x, int y, int width, int height) {}

    public Bounds bounds(String id) {
        JsonObject node = node(id);
        int[] parent = containing(root, node, width(), height());
        return bounds(node, parent == null ? width() : parent[0], parent == null ? height() : parent[1]);
    }

    public JsonObject node(String id) {
        JsonObject found = find(root, id);
        if (found == null) throw new IllegalArgumentException("Missing source UI node: " + id);
        return found;
    }

    private static JsonObject find(JsonObject parent, String id) {
        if (parent.has("id") && id.equals(
            parent.get("id")
                .getAsString()))
            return parent;
        if (parent.has("children")) for (var child : parent.getAsJsonArray("children")) {
            JsonObject found = find(child.getAsJsonObject(), id);
            if (found != null) return found;
        }
        return null;
    }

    public void place(Widget<?> widget, String id) {
        place(widget, node(id));
    }

    /** Each containing block owns its children's right/bottom anchors, as in the source JSON loader. */
    public static void place(Widget<?> widget, JsonObject node, int parentWidth, int parentHeight) {
        if (node.has("id")) widget.name(
            node.get("id")
                .getAsString());
        if (!node.has("layout")) return;
        Bounds box = bounds(node, parentWidth, parentHeight);
        widget.pos(box.x(), box.y())
            .size(box.width(), box.height());
    }

    public static Bounds bounds(JsonObject node, int parentWidth, int parentHeight) {
        JsonObject box = node.has("layout") ? node.getAsJsonObject("layout") : new JsonObject();
        int width = dimension(box, "width", parentWidth, parentWidth);
        int height = dimension(box, "height", parentHeight, parentHeight);
        int x = box.has("left") ? dimension(box, "left", parentWidth, 0)
            : box.has("right") ? parentWidth - width - dimension(box, "right", parentWidth, 0) : 0;
        int y = box.has("top") ? dimension(box, "top", parentHeight, 0)
            : box.has("bottom") ? parentHeight - height - dimension(box, "bottom", parentHeight, 0) : 0;
        return new Bounds(x, y, width, height);
    }

    private static int dimension(JsonObject box, String key, int parent, int fallback) {
        if (!box.has(key)) return fallback;
        String value = box.get(key)
            .getAsString();
        return value.endsWith("%") ? Math.round(parent * Float.parseFloat(value.substring(0, value.length() - 1)) / 100)
            : Math.round(
                box.get(key)
                    .getAsFloat());
    }

    /** Native widgets keep their real slot/action synchronization; the source tree owns geometry. */
    public void children(ParentWidget<?> parent, Function<JsonObject, Widget<?>> binding) {
        children(parent, root, width(), height(), binding);
    }

    private static void children(ParentWidget<?> parent, JsonObject definition, int width, int height,
        Function<JsonObject, Widget<?>> binding) {
        if (!definition.has("children")) return;
        for (var value : definition.getAsJsonArray("children")) {
            JsonObject node = value.getAsJsonObject();
            String type = node.has("type") ? node.get("type")
                .getAsString() : "element";
            Widget<?> widget;
            switch (type) {
                case "element":
                    widget = new ParentWidget<>().background(IDrawable.EMPTY);
                    break;
                case "texture":
                    var sprite = node.getAsJsonArray("sprite");
                    widget = new Widget<>().background(
                        texture(
                            node.get("texture")
                                .getAsString(),
                            sprite.get(0)
                                .getAsInt(),
                            sprite.get(1)
                                .getAsInt(),
                            sprite.get(2)
                                .getAsInt(),
                            sprite.get(3)
                                .getAsInt()));
                    break;
                case "player_inventory":
                    widget = playerInventory();
                    break;
                default:
                    widget = binding.apply(node);
                    break;
            }
            if (widget == null) throw new IllegalArgumentException("Unbound source UI node: " + node);
            place(widget, node, width, height);
            parent.child(widget);
            if (type.equals("element") && widget instanceof ParentWidget<?>) {
                JsonObject box = node.has("layout") ? node.getAsJsonObject("layout") : new JsonObject();
                children(
                    (ParentWidget<?>) widget,
                    node,
                    dimension(box, "width", width, width),
                    dimension(box, "height", height, height),
                    binding);
            }
        }
    }

    public static SlotGroupWidget playerInventory() {
        return SlotGroupWidget.playerInventory((index, slot) -> new NativeItemSlot().background(IDrawable.EMPTY));
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
