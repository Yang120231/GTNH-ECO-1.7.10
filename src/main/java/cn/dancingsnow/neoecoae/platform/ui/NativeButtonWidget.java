package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** LDLib's separate hovered/pressed states without moving the native hitbox. */
public class NativeButtonWidget<W extends NativeButtonWidget<W>> extends ButtonWidget<W> {

    private boolean pressed;
    private IDrawable pressedBackground;

    public W pressedBackground(IDrawable background) {
        this.pressedBackground = background;
        return getThis();
    }

    public boolean isPressed() {
        return pressed;
    }

    @Override
    public Result onMousePressed(int button) {
        if (button == 0) pressed = true;
        return super.onMousePressed(button);
    }

    @Override
    public boolean onMouseRelease(int button) {
        if (button == 0) pressed = false;
        return super.onMouseRelease(button);
    }

    @Override
    public void onMouseEndHover() {
        super.onMouseEndHover();
        pressed = false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (isPressed() && pressedBackground != null) {
            pressedBackground.draw(context, 0, 0, getArea().width, getArea().height, theme.getTheme());
        } else {
            super.drawBackground(context, theme);
        }
    }
}
