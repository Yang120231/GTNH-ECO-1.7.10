package cn.dancingsnow.neoecoae.platform.ui;

import java.util.function.Supplier;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import cn.dancingsnow.neoecoae.platform.minecraft.Component;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A fitted source label. The full translated value remains available in its tooltip. */
public final class NativeText extends Widget<NativeText> {

    private final Supplier<Component> value;
    private final float maxScale;
    private final int color;

    public NativeText(Supplier<Component> value) {
        this(value, .75f, 0x3f3d52);
    }

    public NativeText(Supplier<Component> value, float maxScale, int color) {
        this.value = value;
        this.maxScale = maxScale;
        this.color = color;
        tooltipDynamic(
            tooltip -> tooltip.add(
                value.get()
                    .nativeText()
                    .getFormattedText()));
        onUpdateListener(widget -> widget.markTooltipDirty());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        draw(value.get(), getArea().width, getArea().height, maxScale, color);
    }

    @SideOnly(Side.CLIENT)
    public static void draw(Component value, int width, int height, float maxScale, int fallbackColor) {
        if (value == null || width <= 0 || height <= 0) return;
        var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        String text = value.nativeText()
            .getFormattedText();
        float scale = Math.min(Math.min(maxScale, height / 9f), width / (float) Math.max(1, font.getStringWidth(text)));
        int color = value.getStyle()
            .getColor() == null ? fallbackColor
                : value.getStyle()
                    .getColor()
                    .getValue();
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            org.lwjgl.opengl.GL11.glTranslatef(0, (height - 9 * scale) / 2, 0);
            org.lwjgl.opengl.GL11.glScalef(scale, scale, 1);
            font.drawString(text, 0, 0, color);
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }
}
