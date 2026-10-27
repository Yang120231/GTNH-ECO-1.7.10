package cn.dancingsnow.neoecoae.platform.ui;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The GUI atlas's 12x120 source sheet, sampled as ten 12px frames with its three-tick interpolation. */
public final class NativeAnimatedTexture {

    private static final ResourceLocation INFINITY = ResourceLocation
        .parse("neoecoae:textures/guis/storage/storage_ifinity_icon.png");

    private NativeAnimatedTexture() {}

    public static int infinityFrame(long millis) {
        return (int) Math.floorMod(millis / 150, 10);
    }

    @SideOnly(Side.CLIENT)
    public static void infinity(int x, int y, int width, int height) {
        long time = System.nanoTime() / 1_000_000L;
        int frame = infinityFrame(time);
        NativeSpriteTexture.of(INFINITY)
            .setSprite(0, frame * 12, 12, 12)
            .draw(x, y, width, height);
        int alpha = (int) ((time % 150) * 255 / 150);
        NativeSpriteTexture.of(INFINITY)
            .setSprite(0, (frame + 1) % 10 * 12, 12, 12)
            .setColor(alpha << 24 | 0xffffff)
            .draw(x, y, width, height);
    }
}
