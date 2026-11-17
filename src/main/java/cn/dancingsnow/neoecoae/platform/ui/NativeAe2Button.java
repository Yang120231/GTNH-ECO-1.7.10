package cn.dancingsnow.neoecoae.platform.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** AE2 amount/status buttons share the source's text offsets and state colors. */
public final class NativeAe2Button extends NativeButtonWidget<NativeAe2Button> {

    private final Supplier<Component> label;
    private BooleanSupplier active = () -> true;

    public NativeAe2Button(Supplier<Component> label) {
        this.label = label;
        background(IDrawable.EMPTY);
        hoverBackground(IDrawable.EMPTY);
    }

    public NativeAe2Button activeWhen(BooleanSupplier active) {
        this.active = active;
        return this;
    }

    @Override
    public Result onMousePressed(int button) {
        return active.getAsBoolean() ? super.onMousePressed(button) : Result.IGNORE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        drawFrame(0, 0, getArea().width, getArea().height, active.getAsBoolean(), isHovering() || isPressed());
    }

    /** Shared with the upload window, which keeps the terminal's container open in a plain GuiScreen. */
    @SideOnly(Side.CLIENT)
    public static void drawFrame(int x, int y, int width, int height, boolean enabled, boolean highlighted) {
        String state = !enabled ? "_disabled" : highlighted ? "_highlighted" : "";
        NativePageLayout
            .texture("neoecoae:textures/gui/vendor/ae2/sprites/button" + state + ".png", 0, 0, 200, 20, 3, 3)
            .draw(x, y, width, height);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        drawLabel(
            label.get(),
            0,
            0,
            getArea().width,
            getArea().height,
            1,
            active.getAsBoolean(),
            isHovering() || isPressed(),
            0xf2f2f2);
    }

    @SideOnly(Side.CLIENT)
    public static void drawLabel(Component label, int x, int y, int width, int height, float scale, boolean enabled,
        boolean highlighted, int normalColor) {
        int offset = contentOffset(enabled, highlighted);
        int color = !enabled ? 0x413f54 : highlighted ? 0x517497 : normalColor;
        NativeUiDraw
            .text(label, x + 2, y + (int) ((height - 9 * scale) / 2) + offset, width - 4, scale, color, true, false);
    }

    /** Vertical movement of both text and icons with the AE2 button face. */
    public static int contentOffset(boolean enabled, boolean highlighted) {
        return !enabled ? 2 : highlighted ? 1 : 0;
    }
}
