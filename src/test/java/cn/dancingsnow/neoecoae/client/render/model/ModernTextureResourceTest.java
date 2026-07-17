package cn.dancingsnow.neoecoae.client.render.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ModernTextureResourceTest {

    private static final String COOLANT_TEXTURE = "/assets/neoecoae/textures/blocks/compute/coolant.png";

    @Test
    void coolantAnimationUsesLegacyAtlasCompatibleRgbaFrames() throws IOException {
        try (InputStream stream = ModernTextureResourceTest.class.getResourceAsStream(COOLANT_TEXTURE)) {
            assertNotNull(stream, "coolant texture must be packaged as a classpath resource");
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, "coolant texture must be a readable PNG");
            assertEquals(16, image.getWidth(), "coolant animation frames must be 16 pixels wide");
            assertEquals(0, image.getHeight() % image.getWidth(), "animation strip must contain square frames");
            assertFalse(
                image.getColorModel() instanceof IndexColorModel,
                "legacy 1.7.10 texture atlas cannot reliably stitch indexed-color animated textures");
        }
    }
}
