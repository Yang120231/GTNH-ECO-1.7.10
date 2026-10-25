package cn.dancingsnow.neoecoae.platform.ui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pixel crops and nine-slice borders from source artwork, drawn by the real MUI2 texture backend. */
public final class NativeSpriteTexture implements IDrawable {

    public enum WrapMode {
        STRETCH,
        REPEAT
    }

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
    public WrapMode wrapMode = WrapMode.STRETCH;

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
        return this;
    }

    public NativeSpriteTexture setBorder(int left, int top, int right, int bottom) {
        borderLT = new Point(left, top);
        borderRB = new Point(right, bottom);
        return this;
    }

    public NativeSpriteTexture setColor(int color) {
        this.color = color;
        return this;
    }

    public NativeSpriteTexture setWrapMode(WrapMode wrapMode) {
        this.wrapMode = wrapMode;
        return this;
    }

    public NativeSpriteTexture copy() {
        return of(image).setSprite(spritePosition.x, spritePosition.y, spriteSize.width, spriteSize.height)
            .setBorder(borderLT.x, borderLT.y, borderRB.x, borderRB.y)
            .setColor(color)
            .setWrapMode(wrapMode);
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
        int left = Math.min(borderLT.x, width / 2), right = Math.min(borderRB.x, width - left);
        int top = Math.min(borderLT.y, height / 2), bottom = Math.min(borderRB.y, height - top);
        int[] sx = { 0, borderLT.x, spriteSize.width - borderRB.x, spriteSize.width };
        int[] sy = { 0, borderLT.y, spriteSize.height - borderRB.y, spriteSize.height };
        int[] dx = { 0, left, width - right, width }, dy = { 0, top, height - bottom, height };
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
        try {
            org.lwjgl.opengl.GL11.glColor4f(
                (color >>> 16 & 255) / 255f,
                (color >>> 8 & 255) / 255f,
                (color & 255) / 255f,
                (color >>> 24 & 255) / 255f);
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
                int sw = sx[column + 1] - sx[column], sh = sy[row + 1] - sy[row];
                int dw = dx[column + 1] - dx[column], dh = dy[row + 1] - dy[row];
                if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) continue;
                if (wrapMode == WrapMode.REPEAT && column == 1 && row == 1) {
                    for (int oy = 0; oy < dh; oy += sh) for (int ox = 0; ox < dw; ox += sw) {
                        int pw = Math.min(sw, dw - ox), ph = Math.min(sh, dh - oy);
                        slice(sx[column], sy[row], pw, ph, x + dx[column] + ox, y + dy[row] + oy, pw, ph);
                    }
                } else slice(sx[column], sy[row], sw, sh, x + dx[column], y + dy[row], dw, dh);
            }
        } finally {
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }

    @SideOnly(Side.CLIENT)
    private void slice(int sx, int sy, int sw, int sh, int x, int y, int width, int height) {
        NativePageLayout.texture(image.toString(), spritePosition.x + sx, spritePosition.y + sy, sw, sh)
            .draw(x, y, width, height);
    }
}
