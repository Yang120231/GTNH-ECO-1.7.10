package cn.dancingsnow.neoecoae.platform.ui.storage;

import java.util.regex.Pattern;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.google.gson.JsonObject;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cn.dancingsnow.neoecoae.platform.ui.NativeAe2Button;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Source priority popup geometry and controls; MUI2 supplies native dragging and panel lifetime. */
public final class NativeStoragePriorityPages {

    private NativeStoragePriorityPages() {}

    public static ModularPanel create(NativeStoragePrioritySession session, IPanelHandler handler) {
        // Client-only setters must be resolved when invoked, after common server-side UI construction.
        return create(
            session::priority,
            text -> session.set(text),
            delta -> session.change(delta),
            session::title,
            session::editable,
            handler);
    }

    /** Same priority editor for any server-owned priority setting. */
    public static ModularPanel create(java.util.function.IntSupplier priority, java.util.function.Consumer<String> set,
        java.util.function.IntConsumer change, java.util.function.Supplier<Component> title,
        java.util.function.BooleanSupplier editable, IPanelHandler handler) {
        NativePageLayout layout = new NativePageLayout("storage/storage_priority");
        ModularPanel panel = ModularPanel.defaultPanel("neoecoae_storage_priority", layout.width(), layout.height())
            .background(NativePageLayout.texture("neoecoae:textures/guis/priority.png", 0, 0, 176, 125));
        children(panel, layout, layout.root(), priority, set, change, title, editable, handler);
        return panel;
    }

    private static void children(ParentWidget<?> parent, NativePageLayout layout, JsonObject definition,
        java.util.function.IntSupplier priority, java.util.function.Consumer<String> set,
        java.util.function.IntConsumer change, java.util.function.Supplier<Component> title,
        java.util.function.BooleanSupplier editable, IPanelHandler handler) {
        if (!definition.has("children")) return;
        for (var value : definition.getAsJsonArray("children")) {
            JsonObject node = value.getAsJsonObject();
            String custom = node.get("custom")
                .getAsString();
            Widget<?> widget;
            switch (custom) {
                case "drag_bar":
                    ParentWidget<?> dragBar = new ParentWidget<>();
                    children(dragBar, layout, node, priority, set, change, title, editable, handler);
                    widget = dragBar;
                    break;
                case "title":
                    widget = new NativeText(title);
                    break;
                case "back":
                    widget = new ButtonWidget<>()
                        .background(
                            NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/states.png", 160, 192, 20, 20))
                        .disableHoverBackground()
                        .overlay(
                            NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/states.png", 96, 16, 16, 16))
                        .onMousePressed(button -> {
                            if (button != 0) return false;
                            handler.closePanel();
                            return true;
                        })
                        .tooltipDynamic(
                            tooltip -> tooltip.add(
                                Component.translatable("gui.neoecoae.storage_priority.close")
                                    .getString()));
                    break;
                case "priority_button":
                    int step = node.get("step")
                        .getAsInt();
                    widget = new NativeAe2Button(() -> Component.literal(step > 0 ? "+" + step : String.valueOf(step)))
                        .activeWhen(() -> editable.getAsBoolean())
                        .onMousePressed(button -> {
                            if (button != 0) return false;
                            change.accept(step);
                            return true;
                        });
                    break;
                case "priority_field":
                    widget = new PriorityField().setMaxLength(11)
                        .setEnabledIf(field -> editable.getAsBoolean())
                        .setPattern(Pattern.compile("[+-]?[0-9]*"))
                        .setTextColor(0xfff2f2f2)
                        // Common UI construction must not resolve a method stripped on dedicated servers.
                        .value(new StringValue.Dynamic(() -> String.valueOf(priority.getAsInt()), set));
                    break;
                case "help":
                    String key = node.get("key")
                        .getAsString();
                    widget = new NativeText(() -> Component.translatable(key), 6f / 9, 0x3f3d52);
                    break;
                default:
                    throw new IllegalArgumentException("Unbound source priority node: " + custom);
            }
            layout.place(widget, node);
            parent.child(widget);
        }
    }

    private static final class PriorityField extends TextFieldWidget {

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            NativePageLayout
                .texture("neoecoae:textures/gui/vendor/ae2/text_field.png", 0, isFocused() ? 24 : 0, 128, 12, 1, 0)
                .draw(0, 0, getArea().width, getArea().height);
        }
    }
}
