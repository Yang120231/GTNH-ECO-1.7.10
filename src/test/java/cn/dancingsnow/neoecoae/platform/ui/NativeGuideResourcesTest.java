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
            String[] pages = locale.equals("_zh_cn")
                ? new String[] { "index", "energized_crystal", "integrated_working_station",
                    "large_integrated_working_station", "multiblock_builder", "storage_system",
                    "compat_storage_matrices", "computation_system", "crafting_system" }
                : new String[] { "storage_system", "computation_system", "crafting_system", "integrated_working_station",
                    "large_integrated_working_station", "multiblock_builder", "index" };
            for (String page : pages) {
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
    void chineseMachinePagesUseBlockImagesForSingleBlocksAndScenesForStructures() throws Exception {
        String root = "/assets/neoecoae/guidenh/_zh_cn/neoecoae_intro/";
        for (String system : new String[] { "storage", "computation", "crafting" }) {
            String page = resource(root + system + "_system.md");
            assertTrue(page.contains("<BlockImage id=\"neoecoae:" + system + "_system_l9\""), system);
            assertTrue(page.contains("<BlockImage id=\"neoecoae:" + system + "_interface\""), system);
            assertTrue(page.contains("<ImportStructureLib controller=\"neoecoae:" + system + "_system_l9\""), system);
            assertEquals(1, page.split("interactive=\\{true\\}", -1).length - 1, system);
            assertFalse(page.contains("<Block id="), system);
            assertFalse(page.contains("<ImportStructure src="), system);
        }
        assertTrue(
            resource(root + "integrated_working_station.md")
                .contains("<BlockImage id=\"neoecoae:integrated_working_station\""));
        assertFalse(resource(root + "integrated_working_station.md").contains("<GameScene"));
        String large = resource(root + "large_integrated_working_station.md");
        assertTrue(large.contains("<ImportStructureLib controller=\"neoecoae:integrated_working_station\""));
        assertFalse(large.contains("p:formed="));
    }

    @Test
    void chineseNavigationParentsResolveInsideTheGuideAndCrystalHasBodyIcons() throws Exception {
        String root = "/assets/neoecoae/guidenh/_zh_cn/";
        for (String page : new String[] { "index", "energized_crystal", "integrated_working_station",
            "large_integrated_working_station", "multiblock_builder", "storage_system", "compat_storage_matrices",
            "computation_system", "crafting_system" }) {
            String markdown = resource(root + "neoecoae_intro/" + page + ".md");
            assertTrue(markdown.contains("  icon: neoecoae:"), page);
            String expectedParent = page.equals("index") ? "index.md"
                : "neoecoae_intro/" + (page.equals("compat_storage_matrices") ? "storage_system.md" : "index.md");
            assertTrue(markdown.contains("  parent: neoecoae:" + expectedParent + "\n"), page);
            resource(root + expectedParent);
        }
        String crystal = resource(root + "neoecoae_intro/energized_crystal.md");
        assertTrue(crystal.contains("<ItemIcon id=\"neoecoae:energized_crystal\""));
        assertTrue(crystal.contains("<ItemIcon id=\"neoecoae:energized_crystal_dust\""));
        assertTrue(crystal.contains("<ItemIcon id=\"neoecoae:energized_crystal_block\""));
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
