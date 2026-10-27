package cn.dancingsnow.neoecoae.platform.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Map;

import net.minecraft.util.StringTranslate;

import org.junit.jupiter.api.Test;

class SourceFluidsTest {

    @Test
    void cryotheumUsesTheTranslationKeyLoadedByTheLegacyClient() throws Exception {
        String key = SourceFluids.createCryotheum()
            .getUnlocalizedName();
        assertEquals("fluid.neoecoae.cryotheum_solution", key);
        for (String locale : new String[] { "en_US", "zh_CN" }) {
            try (InputStream stream = getClass().getResourceAsStream("/assets/neoecoae/lang/" + locale + ".lang")) {
                assertNotNull(stream);
                Map<String, String> translations = StringTranslate.parseLangFile(stream);
                assertNotNull(translations.get(key), locale);
                assertFalse(
                    translations.get(key)
                        .startsWith("fluid."),
                    locale);
            }
        }
    }
}
