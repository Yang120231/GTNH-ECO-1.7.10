package cn.dancingsnow.neoecoae.platform.recipe;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GTRecipeProfileTest {

    @Test
    void allCopiedGtRecipesAndPrivateTagsMatchTheirRecordedSourceBytes() throws Exception {
        var catalog = SourceRecipes.read(GTRecipeProfile.CATALOG);
        assertEquals(
            "024b73da2eed6113934d39f0a684e3dc92177c20",
            catalog.get("source_commit")
                .getAsString());
        assertEquals(
            99,
            catalog.getAsJsonArray("files")
                .size());
        assertEquals(
            19,
            catalog.getAsJsonArray("excluded_files")
                .size());
        Set<String> resources = new HashSet<>();
        int machines = 0, tags = 0, workstations = 0, disabled = 0;
        for (var entry : catalog.getAsJsonArray("files")) {
            var file = entry.getAsJsonObject();
            String resource = file.get("resource")
                .getAsString();
            assertTrue(resources.add(resource));
            byte[] content = Files.readAllBytes(
                Paths.get("src/main/resources")
                    .resolve(resource));
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(content);
            StringBuilder hash = new StringBuilder();
            for (byte value : digest) hash.append(String.format("%02x", value & 255));
            assertEquals(
                file.get("sha256")
                    .getAsString(),
                hash.toString(),
                resource);
            var original = SourceRecipes.read(resource);
            String before = original.toString();
            var adapted = GTRecipeProfile.normalize(original);
            assertEquals(before, original.toString(), "Source JSON must not be mutated");
            if (file.get("kind")
                .getAsString()
                .equals("tag")) {
                tags++;
                continue;
            }
            String type = original.get("type")
                .getAsString();
            if (file.get("profile")
                .getAsString()
                .equals("gt_legacy")) {
                machines++;
                assertEquals(original.get("duration"), adapted.get("duration"));
                assertEquals(original.get("tickInputs"), adapted.get("tickInputs"));
                assertEquals(original.get("outputs"), adapted.get("outputs"));
                assertEquals(original.get("data"), adapted.get("data"));
                assertEquals(original.get("recipeConditions"), adapted.get("recipeConditions"));
                assertTrue(
                    adapted.getAsJsonObject("inputs")
                        .getAsJsonArray("item")
                        .size() <= maxInputs(type));
            } else if (type.equals("neoecoae:integrated_working_station")) {
                workstations++;
                assertEquals(original.get("energy"), adapted.get("energy"));
                assertEquals(
                    100,
                    adapted.get("processingTicks")
                        .getAsInt());
                if (original.has("itemOutput")) {
                    assertEquals(
                        original.getAsJsonObject("itemOutput")
                            .get("item"),
                        adapted.getAsJsonObject("itemOutput")
                            .get("id"));
                    assertEquals(
                        original.getAsJsonObject("itemOutput")
                            .get("count"),
                        adapted.getAsJsonObject("itemOutput")
                            .get("count"));
                }
                if (original.has("inputFluid")) assertEquals(original.get("inputFluid"), adapted.get("inputFluid"));
                for (var input : adapted.getAsJsonArray("inputItems")) assertTrue(
                    input.getAsJsonObject()
                        .get("count")
                        .getAsInt() > 0);
            } else if (type.equals("forge:conditional")) disabled++;
        }
        assertEquals(19, machines);
        assertEquals(6, tags);
        assertEquals(30, workstations);
        assertEquals(27, disabled);
    }

    @Test
    void nativeSiliconTagUsesSolarGradePlateWithoutChangingTheLockedSource() {
        var source = SourceRecipes.read("neoecoae/gt-1201/gtm/data/neoecoae/tags/items/compat/gt/silicon.json");
        String original = source.toString();
        var adapted = GTRecipeProfile.normalizeTag("neoecoae:compat/gt/silicon", source);
        var values = adapted.getAsJsonArray("values");
        assertEquals(2, values.size());
        assertEquals(
            "ae2:silicon",
            values.get(0)
                .getAsJsonObject()
                .get("id")
                .getAsString());
        assertEquals(
            "gtceu:silicon_solar_grade_plate",
            values.get(1)
                .getAsJsonObject()
                .get("id")
                .getAsString());
        for (var entry : values) assertFalse(
            entry.getAsJsonObject()
                .get("required")
                .getAsBoolean());
        assertFalse(
            adapted.get("replace")
                .getAsBoolean());
        assertEquals(original, source.toString());
        assertEquals(
            3,
            source.getAsJsonArray("values")
                .size());
        assertEquals(
            original,
            GTRecipeProfile.normalizeTag("neoecoae:compat/gt/other", source)
                .toString());
    }

    @Test
    void gtCircuitProgressionUsesTheNativeGtnhTierNames() {
        assertEquals("circuitAdvanced", SourceTags.oreName("gtceu:circuits/hv"));
        assertEquals("circuitData", SourceTags.oreName("gtceu:circuits/ev"));
        assertEquals("circuitElite", SourceTags.oreName("gtceu:circuits/iv"));
        assertEquals("circuitUltimate", SourceTags.oreName("gtceu:circuits/zpm"));
        assertThrows(IllegalArgumentException.class, () -> SourceTags.oreName("gtceu:circuits/unknown"));
    }

    @Test
    void gtBootstrapRetainsEvControllerCleanroomAndBlastHeatRequirements() {
        String base = "neoecoae/gt-1201/gt_legacy/data/neoecoae/recipes/compat/gt/bootstrap/";
        var controller = SourceRecipes.read(base + "b10_integrated_working_station.json");
        assertEquals(
            600,
            controller.get("duration")
                .getAsInt());
        assertEquals(
            1920,
            controller.getAsJsonObject("tickInputs")
                .getAsJsonArray("eu")
                .get(0)
                .getAsJsonObject()
                .get("content")
                .getAsInt());
        assertEquals(
            9,
            controller.getAsJsonObject("inputs")
                .getAsJsonArray("item")
                .size());
        var blast = SourceRecipes.read(base + "b04_black_tungsten_alloy_ingot.json");
        assertEquals(
            3600,
            blast.getAsJsonObject("data")
                .get("ebf_temp")
                .getAsInt());
        var processor = SourceRecipes.read(base + "b09_superconducting_processor.json");
        assertEquals(
            "cleanroom",
            processor.getAsJsonArray("recipeConditions")
                .get(0)
                .getAsJsonObject()
                .get("type")
                .getAsString());
        var crystals = GTRecipeProfile.normalize(
            SourceRecipes.read("neoecoae/gt-1201/gtm/data/neoecoae/tags/items/compat/gt/crystal_bases.json"));
        assertEquals(
            "#c:gems/diamond",
            crystals.getAsJsonArray("values")
                .get(0)
                .getAsString());
    }

    private static int maxInputs(String type) {
        switch (type) {
            case "gtceu:mixer":
            case "gtceu:assembler":
                return 9;
            case "gtceu:autoclave":
            case "gtceu:alloy_smelter":
                return 2;
            case "gtceu:electric_blast_furnace":
            case "gtceu:circuit_assembler":
                return 6;
            case "gtceu:macerator":
                return 1;
            default:
                throw new AssertionError("Unreviewed source machine " + type);
        }
    }
}
