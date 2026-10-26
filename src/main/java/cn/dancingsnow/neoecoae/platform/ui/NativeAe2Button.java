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
        boolean enabled = active.getAsBoolean();
        String state = !enabled ? "_disabled" : isHovering() || isPressed() ? "_highlighted" : "";
        NativePageLayout.texture("neoecoae:textures/gui/vendor/ae2/sprites/button" + state + ".png",
            0, 0, 200, 20, 3, 3).draw(context, 0, 0, getArea().width, getArea().height, theme.getTheme());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        boolean enabled = active.getAsBoolean();
        boolean highlighted = isHovering() || isPressed();
        int offset = !enabled ? 2 : highlighted ? 1 : 0;
        int color = !enabled ? 0x413f54 : highlighted ? 0x517497 : 0xf2f2f2;
        NativeUiDraw.text(label.get(), 2, (getArea().height - 9) / 2 + offset,
            getArea().width - 4, 1, color, true, false);
    }
}
