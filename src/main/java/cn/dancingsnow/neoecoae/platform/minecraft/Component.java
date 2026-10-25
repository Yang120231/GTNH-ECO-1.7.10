package cn.dancingsnow.neoecoae.platform.minecraft;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

public final class Component {

    private final IChatComponent nativeText;
    private Integer rgb;
    private final java.util.Map<String, Integer> nestedColors = new java.util.TreeMap<>();

    private Component(IChatComponent text) {
        nativeText = text;
    }

    public static Component fromNative(IChatComponent value) {
        return value == null ? empty() : new Component(value.createCopy());
    }

    public static Component literal(String text) {
        return new Component(new ChatComponentText(text));
    }

    public static Component empty() {
        return literal("");
    }

    public static Component translatable(String key, Object... args) {
        Object[] nativeArgs = args.clone();
        for (int index = 0; index < args.length; index++)
            if (args[index] instanceof Component) nativeArgs[index] = ((Component) args[index]).nativeText();
        Component result = new Component(new ChatComponentTranslation(key, nativeArgs));
        for (int index = 0; index < args.length; index++) if (args[index] instanceof Component) {
            Component value = (Component) args[index];
            String prefix = "arg:" + index;
            if (value.rgb != null) result.nestedColors.put(prefix, value.rgb);
            value.nestedColors.forEach((path, color) -> result.nestedColors.put(prefix + "/" + path, color));
        }
        return result;
    }

    public Component copy() {
        Component copy = new Component(nativeText.createCopy());
        copy.rgb = rgb;
        copy.nestedColors.putAll(nestedColors);
        return copy;
    }

    public Component withColor(int color) {
        rgb = color & 0xffffff;
        return this;
    }

    public Component append(Component value) {
        String path = Integer.toString(
            nativeText.getSiblings()
                .size());
        if (value.rgb != null) nestedColors.put(path, value.rgb);
        value.nestedColors.forEach((key, color) -> nestedColors.put(path + "/" + key, color));
        nativeText.appendSibling(value.nativeText());
        return this;
    }

    public Component append(String value) {
        nativeText.appendText(value);
        return this;
    }

    public TextStyle getStyle() {
        return new TextStyle(rgb);
    }

    public static final class TextStyle {
        public static final TextStyle EMPTY = new TextStyle(null);

        private final Integer color;

        TextStyle(Integer color) {
            this.color = color;
        }

        public TextColor getColor() {
            return color == null ? null : new TextColor(color);
        }
    }

    public static final class TextColor {

        private final int value;

        TextColor(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public Component withStyle(net.minecraft.util.EnumChatFormatting... styles) {
        net.minecraft.util.ChatStyle style = nativeText.getChatStyle();
        for (net.minecraft.util.EnumChatFormatting value : styles) {
            switch (value) {
                case BOLD:
                    style.setBold(true);
                    break;
                case ITALIC:
                    style.setItalic(true);
                    break;
                case UNDERLINE:
                    style.setUnderlined(true);
                    break;
                case STRIKETHROUGH:
                    style.setStrikethrough(true);
                    break;
                case OBFUSCATED:
                    style.setObfuscated(true);
                    break;
                case RESET:
                    style = new net.minecraft.util.ChatStyle();
                    break;
                default:
                    style.setColor(value);
                    break;
            }
        }
        nativeText.setChatStyle(style);
        return this;
    }

    public String getString() {
        return nativeText.getUnformattedText();
    }

    public IChatComponent nativeText() {
        return nativeText.createCopy();
    }
    public <T> java.util.Optional<T> visit(java.util.function.BiFunction<TextStyle, String, java.util.Optional<T>> visitor, TextStyle inherited) {
        return visitNode(nativeText, "", rgb == null ? inherited.color : rgb, visitor);
    }
    private <T> java.util.Optional<T> visitNode(IChatComponent node, String path, Integer inherited,
        java.util.function.BiFunction<TextStyle, String, java.util.Optional<T>> visitor) {
        Integer color = path.isEmpty() ? rgb == null ? inherited : rgb : nestedColors.getOrDefault(path, inherited);
        java.util.Optional<T> result = visitor.apply(new TextStyle(color), node.getUnformattedTextForChat());
        if (result.isPresent()) return result;
        for (int index = 0; index < node.getSiblings().size(); index++) {
            String child = path.isEmpty() ? Integer.toString(index) : path + "/" + index;
            result = visitNode(node.getSiblings().get(index), child, color, visitor);
            if (result.isPresent()) return result;
        }
        return java.util.Optional.empty();
    }

    /** Native JSON retains legacy styles; the envelope retains arbitrary RGB values unavailable in 1.7 chat styles. */
    public String toJson() {
        com.google.gson.JsonObject value = new com.google.gson.JsonObject();
        value
            .add("native", new com.google.gson.JsonParser().parse(IChatComponent.Serializer.func_150696_a(nativeText)));
        if (rgb != null) value.addProperty("rgb", rgb);
        com.google.gson.JsonObject colors = new com.google.gson.JsonObject();
        nestedColors.forEach(colors::addProperty);
        value.add("nestedColors", colors);
        return value.toString();
    }

    public static Component fromJson(String encoded) {
        com.google.gson.JsonElement element = new com.google.gson.JsonParser().parse(encoded);
        if (!element.isJsonObject() || !element.getAsJsonObject()
            .has("native")) return fromNative(IChatComponent.Serializer.func_150699_a(encoded));
        com.google.gson.JsonObject value = element.getAsJsonObject();
        Component result = fromNative(
            IChatComponent.Serializer.func_150699_a(
                value.get("native")
                    .toString()));
        if (value.has("rgb")) result.withColor(
            value.get("rgb")
                .getAsInt());
        if (value.has("nestedColors")) for (var entry : value.getAsJsonObject("nestedColors")
            .entrySet())
            result.nestedColors.put(
                entry.getKey(),
                entry.getValue()
                    .getAsInt() & 0xffffff);
        return result;
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof Component && nativeText.equals(((Component) value).nativeText)
            && java.util.Objects.equals(rgb, ((Component) value).rgb)
            && nestedColors.equals(((Component) value).nestedColors);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(nativeText, rgb, nestedColors);
    }

    @Override
    public String toString() {
        return getString();
    }
}
