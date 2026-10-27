package cn.dancingsnow.neoecoae.platform.ui.multiblock;

import java.util.regex.Pattern;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
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
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.ui.NativeAe2Button;
import cn.dancingsnow.neoecoae.platform.ui.NativeButtonWidget;
import cn.dancingsnow.neoecoae.platform.ui.NativeMachinePanel;
import cn.dancingsnow.neoecoae.platform.ui.NativePageLayout;
import cn.dancingsnow.neoecoae.platform.ui.NativeSpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.NativeText;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The 1.20.1 four-row builder layout and artwork, shared by all three native host menus. */
public final class NativeBuilderPages {

    private NativeBuilderPages() {}

    public static ModularPanel create(NativeBuilderSession session, IPanelHandler handler) {
        NativePageLayout layout = new NativePageLayout("common/multiblock_build");
        ModularPanel panel = NativeMachinePanel.create("neoecoae_multiblock_builder", layout.width(), layout.height())
            .background(
                NativeSpriteTexture.of(ResourceLocation.parse("neoecoae:textures/guis/priority.png"))
                    .setSprite(0, 0, 176, 125)
                    .setBorder(1, 1, 1, 3));
        LengthField length = new LengthField(session);
        children(panel, layout, layout.root(), session, handler, length);
        return panel;
    }

    private static void children(ParentWidget<?> parent, NativePageLayout layout, JsonObject definition,
        NativeBuilderSession session, IPanelHandler handler, LengthField length) {
        if (!definition.has("children")) return;
        for (var value : definition.getAsJsonArray("children")) {
            JsonObject node = value.getAsJsonObject();
            Widget<?> widget;
            String type = node.get("type")
                .getAsString();
            if (type.equals("label")) {
                widget = new NativeText(
                    () -> Component.translatable(
                        node.get("text")
                            .getAsString()),
                    1,
                    0x413f54);
            } else if (type.equals("element") || node.get("name")
                .getAsString()
                .equals("drag_bar")) {
                    ParentWidget<?> group = new ParentWidget<>();
                    children(group, layout, node, session, handler, length);
                    widget = group;
                } else {
                    switch (node.get("name")
                        .getAsString()) {
                        case "back":
                            widget = new ButtonWidget<>()
                                .background(
                                    NativePageLayout
                                        .texture("neoecoae:textures/gui/vendor/ae2/states.png", 160, 192, 20, 20))
                                .overlay(
                                    NativePageLayout
                                        .texture("neoecoae:textures/gui/vendor/ae2/states.png", 96, 16, 16, 16))
                                .onMousePressed(button -> {
                                    if (button != 0) return false;
                                    length.commit();
                                    handler.closePanel();
                                    return true;
                                })
                                .tooltipDynamic(tooltip -> tooltip.add(text("close_builder").getString()));
                            break;
                        case "length_field":
                            widget = length;
                            break;
                        case "length_step":
                            int step = node.get("step")
                                .getAsInt();
                            widget = new NativeAe2Button(
                                () -> Component.literal(step > 0 ? "+" + step : String.valueOf(step)))
                                    .activeWhen(session::editable)
                                    .onMousePressed(button -> {
                                        if (button != 0) return false;
                                        length.commit();
                                        session.changeLength(step);
                                        return true;
                                    });
                            break;
                        case "mirror":
                            widget = new MirrorSwitch(session);
                            break;
                        case "build":
                            widget = new NativeAe2Button(() -> text("build")).activeWhen(session::editable)
                                .onMousePressed(button -> {
                                    if (button != 0) return false;
                                    length.commit();
                                    session.build();
                                    return true;
                                })
                                .tooltipDynamic(tooltip -> tooltip.add(text("status." + session.status()).getString()))
                                .onUpdateListener(control -> control.markTooltipDirty());
                            break;
                        default:
                            throw new IllegalArgumentException("Unbound source builder node: " + node);
                    }
                }
            layout.place(widget, node);
            parent.child(widget);
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.neoecoae.multiblock." + key, args);
    }

    private static final class LengthField extends TextFieldWidget {

        private final NativeBuilderSession session;

        private LengthField(NativeBuilderSession session) {
            this.session = session;
            setMaxLength(10);
            setPattern(Pattern.compile("[0-9]*"));
            setTextColor(0xfff2f2f2);
            value(
                new StringValue.Dynamic(
                    () -> String.valueOf(session.length()),
                    text -> { if (session.editable()) session.setLength(text); }));
            tooltipDynamic(
                tooltip -> tooltip.add(
                    Component
                        .translatable(
                            "gui.neoecoae.structure_terminal.length_range",
                            session.minLength(),
                            session.maxLength())
                        .getString()));
        }

        private void commit() {
            // MUI2 normally removes focus after the click callback. Commit first so a later focus-loss
            // packet cannot overwrite the length step or arrive after the build action.
            if (isFocused()) getContext().removeFocus();
        }

        @Override
        public Result onMousePressed(int button) {
            return session.editable() ? super.onMousePressed(button) : Result.IGNORE;
        }

        @Override
        public Result onKeyPressed(char character, int keyCode) {
            return session.editable() ? super.onKeyPressed(character, keyCode) : Result.IGNORE;
        }

        @Override
        public void onUpdate() {
            if (!session.editable()) commit();
            super.onUpdate();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            NativePageLayout
                .texture(
                    "neoecoae:textures/gui/vendor/ae2/text_field.png",
                    0,
                    !session.editable() ? 12 : isFocused() ? 24 : 0,
                    128,
                    12,
                    1,
                    0)
                .draw(0, 0, getArea().width, getArea().height);
        }
    }

    private static final class MirrorSwitch extends NativeButtonWidget<MirrorSwitch> {

        private final NativeBuilderSession session;

        private MirrorSwitch(NativeBuilderSession session) {
            this.session = session;
            background(IDrawable.EMPTY);
            hoverBackground(IDrawable.EMPTY);
            onMousePressed(button -> {
                if (button != 0 || !session.editable()) return false;
                session.mirror(!session.mirrored());
                return true;
            });
            tooltipDynamic(
                tooltip -> tooltip.add(text("mirror." + (session.mirrored() ? "on" : "off") + ".tooltip").getString()));
            onUpdateListener(control -> control.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            NativePageLayout
                .texture(
                    "neoecoae:textures/gui/vendor/ae2/checkbox.png",
                    session.editable() && (isHovering() || isPressed()) ? 22 : 0,
                    session.mirrored() ? 40 : 28,
                    22,
                    12)
                .draw(0, 0, getArea().width, getArea().height);
        }
    }
}
