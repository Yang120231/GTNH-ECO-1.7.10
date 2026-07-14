package cn.dancingsnow.neoecoae.client.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Exact 16x16 pixel copy of LDLib2 {@code Sprites.BORDER_THICK_RT1}. */
@SideOnly(Side.CLIENT)
final class HostPanelBorderTexture {

    // PNG copied byte-for-byte from the LDLib2 host-panel sprite used by the 1.20.1 backport.
    // Keeping the source bytes here avoids substituting a hand-drawn approximation on 1.7.10.
    private static final String PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAACxIAAAsSAdLdfvwAAAB4SURBVDhPY2CAAl5ewf+kYJg+uObWxl6i8cVzVxCGgBjuzv7/E6LSwDQxGKQWbgjMAJDJ+lomGE5FxyA1MPUYBsDY+DC6etoYAPIjOh41gEQD8GF09SgGwBTgw1gTEsyZIEFiMEwt2ABYfkD2JzEYrhk5R5KCYfoArHmyRVtuUaoAAAAASUVORK5CYII=";

    private static ResourceLocation location;

    private HostPanelBorderTexture() {}

    static ResourceLocation location() {
        if (location == null) {
            location = register();
        }
        return location;
    }

    private static ResourceLocation register() {
        try {
            byte[] png = Base64.getDecoder().decode(PNG_BASE64);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
                throw new IOException("Invalid LDLib2 host panel border image");
            }
            return Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("neoecoae_host_panel_border", new DynamicTexture(image));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to load LDLib2 host panel border", exception);
        }
    }
}
