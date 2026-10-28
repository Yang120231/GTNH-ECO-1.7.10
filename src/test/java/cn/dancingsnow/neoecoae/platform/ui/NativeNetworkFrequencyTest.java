package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.StringTranslate;

import org.junit.jupiter.api.Test;

class NativeNetworkFrequencyTest {

    @Test
    void assignedFrequenciesRemainVisibleEvenWhenTheTitleHasNoPlaceholder() throws Exception {
        Map<String, String> original = language("en_US");
        for (String namespace : new String[] { "minecraft", "appliedenergistics2" }) {
            try (InputStream stream = getClass().getResourceAsStream("/assets/" + namespace + "/lang/en_US.lang")) {
                assertNotNull(stream, namespace);
                original.putAll(StringTranslate.parseLangFile(stream));
            }
        }
        try {
            for (String locale : new String[] { "en_US", "zh_CN", "zh_HK", "zh_TW", "lzh", "en_UD" }) {
                Map<String, String> translated = new HashMap<>(original);
                translated.putAll(language(locale));
                StringTranslate.replaceWith(translated);
                for (int frequency = 1; frequency <= 4; frequency++) {
                    String text = NativeHostSideBar.frequencyText(frequency)
                        .getString();
                    assertTrue(text.endsWith(": " + frequency), locale + ": " + text);
                    assertFalse(text.contains("gui."), locale + ": " + text);
                }
                String unassigned = NativeHostSideBar.frequencyText(-1)
                    .getString();
                assertFalse(unassigned.contains("gui."), locale);
                assertFalse(unassigned.contains("-1"), locale);
                assertEquals(
                    unassigned,
                    NativeHostSideBar.frequencyText(0)
                        .getString());
                for (int frequency = 1; frequency <= 4; frequency++) {
                    String tooltip = NativeHostSideBar.frequencyTooltip(frequency)
                        .getString();
                    assertTrue(tooltip.contains(Integer.toString(frequency)), locale + ": " + tooltip);
                    assertFalse(tooltip.contains("gui."), locale + ": " + tooltip);
                }
                assertFalse(
                    NativeHostSideBar.frequencyTooltip(-1)
                        .getString()
                        .contains("gui."),
                    locale);
            }
        } finally {
            StringTranslate.replaceWith(original);
        }
    }

    @Test
    void leftClickAdvancesRightClickGoesBackAndOtherButtonsAreIgnored() {
        assertEquals(1, NativeHostSideBar.frequencyDelta(0));
        assertEquals(-1, NativeHostSideBar.frequencyDelta(1));
        assertEquals(0, NativeHostSideBar.frequencyDelta(2));
    }

    private static Map<String, String> language(String locale) throws Exception {
        try (InputStream stream = NativeNetworkFrequencyTest.class
            .getResourceAsStream("/assets/neoecoae/lang/" + locale + ".lang")) {
            assertNotNull(stream, locale);
            return StringTranslate.parseLangFile(stream);
        }
    }
}
