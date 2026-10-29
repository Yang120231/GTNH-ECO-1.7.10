package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pixel crops and nine-slice borders from source artwork, drawn as a cached MUI2 adaptable texture. */
public final class NativeSpriteTexture implements IDrawable {

    public static final class Point {

        public final int x, y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class Size {

        public final int width, height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private final ResourceLocation image;
    public Point spritePosition = new Point(0, 0), borderLT = new Point(0, 0), borderRB = new Point(0, 0);
    public Size spriteSize = new Size(16, 16);
    public int color = -1;
    private UITexture texture;

    private NativeSpriteTexture(ResourceLocation image) {
        this.image = image;
    }

    public static NativeSpriteTexture of(ResourceLocation image) {
        return new NativeSpriteTexture(image);
    }

    public ResourceLocation getImageLocation() {
        return image;
    }

    public NativeSpriteTexture setSprite(int x, int y, int width, int height) {
        spritePosition = new Point(x, y);
        spriteSize = new Size(width, height);
        texture = null;
        return this;
    }

    public NativeSpriteTexture setBorder(int left, int top, int right, int bottom) {
        borderLT = new Point(left, top);
        borderRB = new Point(right, bottom);
        texture = null;
        return this;
    }

    public NativeSpriteTexture setColor(int color) {
        this.color = color;
        texture = null;
        return this;
    }

    public NativeSpriteTexture copy() {
        return of(image).setSprite(spritePosition.x, spritePosition.y, spriteSize.width, spriteSize.height)
            .setBorder(borderLT.x, borderLT.y, borderRB.x, borderRB.y)
            .setColor(color);
    }

    @Override
    public int getDefaultWidth() {
        return spriteSize.width;
    }

    @Override
    public int getDefaultHeight() {
        return spriteSize.height;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        draw(x, y, width, height);
    }

    @SideOnly(Side.CLIENT)
    public void draw(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        texture().draw(x, y, width, height);
    }

    private UITexture texture() {
        if (texture == null) {
            UITexture base = NativePageLayout.texture(
                image.toString(),
                spritePosition.x,
                spritePosition.y,
                spriteSize.width,
                spriteSize.height,
                borderLT.x,
                borderLT.y,
                borderRB.x,
                borderRB.y);
            texture = color == -1 ? base : base.withColorOverride(color);
        }
        return texture;
    }
}
