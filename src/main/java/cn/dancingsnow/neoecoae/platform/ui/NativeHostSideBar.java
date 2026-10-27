package cn.dancingsnow.neoecoae.platform.ui;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Direct MUI2 implementation of HostSideButtonBar's 23/22px rails and shared 18px upgrade column. */
public final class NativeHostSideBar {

    public static final String ATLAS = "neoecoae:textures/gui/vendor/ae2/states.png";

    private NativeHostSideBar() {}

    public static ParentWidget<?> left(boolean gray, Widget<?>... buttons) {
        ParentWidget<?> bar = new ParentWidget<>().pos(-21, 0)
            .size(23, height(buttons.length))
            .background(IDrawable.EMPTY);
        for (int index = 0; index < buttons.length; index++) {
            int top = index == 0 ? 0 : 23 + (index - 1) * 22;
            int h = index == 0 ? 30 : index == buttons.length - 1 ? 27 : 24;
            String part = index == 0 ? "up" : index == buttons.length - 1 ? "down" : "middle";
            ParentWidget<?> segment = new ParentWidget<>().pos(0, top)
                .size(23, h)
                .background(
                    NativePageLayout.texture(
                        "neoecoae:textures/gui/common/side_bar/" + (gray ? "gray_" : "")
                            + "button_slot_"
                            + part
                            + ".png",
                        0,
                        0,
                        23,
                        h));
            segment.child(
                buttons[index].pos(4, index == 0 ? 3 : 2)
                    .size(16));
            bar.child(segment);
        }
        return bar;
    }

    public static int height(int count) {
        if (count <= 0) throw new IllegalArgumentException("Empty host toolbar");
        return count == 1 ? 30 : 23 + (count - 2) * 22 + 27;
    }

    public static ParentWidget<?> upgrades(int panelWidth, List<? extends Widget<?>> slots) {
        int h = slots.size() * 18;
        ParentWidget<?> bar = new ParentWidget<>().pos(panelWidth - 2, 0)
            .size(23, h + 12)
            .background(
                NativeSpriteTexture
                    .of(
                        cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation
                            .parse("neoecoae:textures/gui/common/side_bar/mirrored_button_slot_up.png"))
                    .setSprite(0, 0, 23, 30)
                    .setBorder(0, 5, 0, 7));
        bar.child(
            new Widget<>().pos(0, 5)
                .size(18, h)
                .background((context, x, y, w, height, theme) -> {
                    com.cleanroommc.modularui.drawable.GuiDraw.drawRect(x - 1, y - 1, w + 2, 1, 0xfff2f2f2);
                    com.cleanroommc.modularui.drawable.GuiDraw.drawRect(x - 1, y + height, w + 2, 1, 0xfff2f2f2);
                    com.cleanroommc.modularui.drawable.GuiDraw.drawRect(x - 1, y, 1, height, 0xfff2f2f2);
                    com.cleanroommc.modularui.drawable.GuiDraw.drawRect(x + w, y, 1, height, 0xfff2f2f2);
                }));
        for (int index = 0; index < slots.size(); index++) bar.child(
            slots.get(index)
                .pos(0, 5 + index * 18)
                .size(18)
                .background(NativePageLayout.texture(ATLAS, 192, 192, 18, 18)));
        return bar;
    }

    public static ButtonWidget<?> button(int u, int v, Supplier<Component> tooltip, Runnable action) {
        return button(() -> NativePageLayout.texture(ATLAS, u, v, 16, 16), tooltip, action);
    }

    public static ButtonWidget<?> button(Supplier<IDrawable> icon, Supplier<Component> tooltip, Runnable action) {
        return new IconButton(icon, tooltip, action, null);
    }

    public static ButtonWidget<?> networkFrequency(IntSupplier frequency, Runnable action) {
        return new IconButton(
            () -> NativePageLayout.texture(ATLAS, 16, 240, 16, 16),
            () -> Component.translatable("gui.neoecoae.host.network_frequency.title"),
            action,
            frequency);
    }

    static Component frequencyText(int frequency) {
        return frequency < 1 ? Component.translatable("gui.neoecoae.host.network_frequency.unassigned")
            : Component.translatable("gui.neoecoae.host.network_frequency.current")
                .append(": " + frequency);
    }

    private static final class IconButton extends NativeButtonWidget<IconButton> {

        private final Supplier<IDrawable> icon;
        private final IntSupplier frequency;

        IconButton(Supplier<IDrawable> icon, Supplier<Component> tooltip, Runnable action, IntSupplier frequency) {
            this.icon = icon;
            this.frequency = frequency;
            background(IDrawable.EMPTY);
            onMousePressed(button -> {
                if (button != 0) return false;
                action.run();
                return true;
            });
            tooltipDynamic(lines -> {
                lines.add(
                    tooltip.get()
                        .getString());
                if (frequency != null) lines.newLine()
                    .add(frequencyText(frequency.getAsInt()).getString());
            });
            onUpdateListener(widget -> widget.markTooltipDirty());
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            boolean highlighted = isHovering() || isPressed();
            NativeUiDraw.sprite(ATLAS, highlighted ? 212 : 176, 128, 18, 20, -1, highlighted ? 1 : 0, 18, 20);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            icon.get()
                .draw(context, 0, isHovering() || isPressed() ? 2 : 1, 16, 16, theme.getTheme());
            if (frequency != null) {
                int value = frequency.getAsInt();
                com.cleanroommc.modularui.drawable.GuiDraw.drawRect(9, 9, 8, 8, 0xff282436);
                NativeUiDraw.text(
                    Component.literal(value < 1 ? "-" : Integer.toString(value)),
                    10,
                    9,
                    6,
                    .75f,
                    0xffffff,
                    true,
                    false);
            }
        }
    }
}
