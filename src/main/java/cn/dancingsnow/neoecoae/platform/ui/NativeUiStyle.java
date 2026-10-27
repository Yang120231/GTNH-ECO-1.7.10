package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** ECO's source skin, shared by direct MUI2 pages instead of the default MUI theme. */
public final class NativeUiStyle {

    private NativeUiStyle() {}

    public static IDrawable panel() {
        return NativeSpriteTexture.of(ResourceLocation.parse("neoecoae:textures/gui/common/background.png"))
            .setSprite(0, 0, 16, 16)
            .setBorder(2, 2, 2, 4);
    }

    public static IDrawable slot() {
        return NativePageLayout.texture("neoecoae:textures/gui/common/slot.png", 0, 0, 18, 18);
    }

    public static IDrawable inventoryBorder() {
        return NativeSpriteTexture.of(ResourceLocation.parse("neoecoae:textures/gui/common/inventory_border.png"))
            .setSprite(0, 0, 16, 16)
            .setBorder(1, 1, 1, 1);
    }

    public static void inventoryFrames(com.cleanroommc.modularui.widget.ParentWidget<?> parent, int x, int y) {
        parent.child(
            new com.cleanroommc.modularui.widget.Widget<>().pos(x - 1, y - 1)
                .size(164, 56)
                .background(inventoryBorder()));
        parent.child(
            new com.cleanroommc.modularui.widget.Widget<>().pos(x - 1, y + 57)
                .size(164, 20)
                .background(inventoryBorder()));
    }

    public static IDrawable button(boolean highlighted) {
        return NativeSpriteTexture
            .of(
                ResourceLocation
                    .parse("neoecoae:textures/gui/common/button" + (highlighted ? "_highlighted" : "") + ".png"))
            .setSprite(0, 0, 20, 20)
            .setBorder(2, highlighted ? 3 : 2, 2, 5);
    }

    public static ButtonWidget<?> button() {
        return new ButtonWidget<>().background(button(false))
            .hoverBackground(button(true));
    }
}
