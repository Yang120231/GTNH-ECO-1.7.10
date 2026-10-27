package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.common.io.ByteStreams;

class NativeGuideResourcesTest {

    @Test
    void guideNhDiscoversBothLanguagesAndAllMachineButtonTargets() throws Exception {
        for (String locale : new String[] { "_en_us", "_zh_cn" }) {
            String root = "/assets/neoecoae/guidenh/" + locale + "/";
            String home = resource(root + "index.md");
            for (String page : new String[] { "storage_system", "computation_system", "crafting_system",
                "integrated_working_station", "large_integrated_working_station", "multiblock_builder", "index" }) {
                String target = "neoecoae_intro/" + page + ".md";
                assertTrue(home.contains(target), locale + ": " + target);
                String markdown = resource(root + target);
                assertTrue(markdown.startsWith("---"), locale + ": " + target);
                assertTrue(markdown.contains("navigation:"), locale + ": " + target);
            }
        }
        for (String asset : new String[] { "scenes/store_min.nbt", "scenes/comp_min.nbt", "scenes/craft_min.nbt",
            "screenshots/crafting/overview.png" }) {
            try (InputStream original = getClass().getResourceAsStream("/assets/neoecoae/ae2guide/" + asset);
                InputStream nativeAsset = getClass().getResourceAsStream("/assets/neoecoae/guidenh/" + asset)) {
                assertNotNull(original, asset);
                assertNotNull(nativeAsset, asset);
                assertArrayEquals(ByteStreams.toByteArray(original), ByteStreams.toByteArray(nativeAsset), asset);
            }
        }
    }

    @Test
    void standaloneGuideCanLoadWithoutGuideNhOnTheRuntimeClasspath() throws Exception {
        assertNotNull(Class.forName(NativeGuidePage.class.getName()));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.hfstudio.guidenh.guide.GuidesCommon"));
    }

    private String resource(String path) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(ByteStreams.toByteArray(stream), StandardCharsets.UTF_8);
        }
    }
}
