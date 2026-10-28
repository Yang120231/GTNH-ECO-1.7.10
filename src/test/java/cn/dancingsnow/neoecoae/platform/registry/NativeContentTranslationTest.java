package cn.dancingsnow.neoecoae.platform.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.StringTranslate;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Every block, item and creative-tab display key the registered content exposes must exist in the shipped en_US and
 * zh_CN files. The keys are read
 * from the real Block/ItemBlock/Item objects, including the no-stack getters AE2 and other mods translate with
 * {@code getUnlocalizedName() + ".name"}, so a key scheme drift cannot hide behind a hard-coded expectation. The
 * fluid key is covered by {@code SourceFluidsTest}.
 */
class NativeContentTranslationTest {

    private static final Path LANG = Paths.get("src/main/resources/assets/neoecoae/lang");
    private static final List<String> LOCALES = Arrays.asList("en_US", "zh_CN");

    @Test
    void everyRegisteredBlockAndItemKeyIsTranslated() throws Exception {
        Map<String, Block> blocks = blocks();
        assertEquals(catalogIds("core-registration.json", "blocks"), blocks.keySet(), "core block coverage");

        Map<String, String> keys = new LinkedHashMap<>();
        for (var entry : blocks.entrySet()) {
            Block block = entry.getValue();
            Item item = new NativeMachineItemBlock(block);
            for (String key : Arrays
                .asList(block.getUnlocalizedName(), item.getUnlocalizedName(), item.getUnlocalizedName(null)))
                keys.put(nameKey(key), "block " + entry.getKey());
        }
        keys.put("tile.neoecoae.platform_blue_ice.name", "block platform_blue_ice");
        for (String id : catalogIds("core-content.json", "items")) {
            Item item = new Item().setUnlocalizedName(NativeContent.translationName(id));
            keys.put(nameKey(item.getUnlocalizedName()), "item " + id);
            keys.put(nameKey(item.getUnlocalizedName(null)), "item " + id);
        }
        keys.put("itemGroup." + NativeContent.TAB.getTabLabel(), "creative tab");

        List<String> missing = new ArrayList<>();
        for (String locale : LOCALES) {
            Map<String, String> translations = lang(locale);
            for (var entry : keys.entrySet()) {
                String value = translations.get(entry.getKey());
                if (value == null || value.isEmpty() || value.equals(entry.getKey()))
                    missing.add(locale + ": " + entry.getKey() + " (" + entry.getValue() + ")");
            }
        }
        assertEquals(Collections.emptyList(), missing);
    }

    @Test
    void shippedLanguagesNeverDoubleTheLegacyNameSuffix() throws Exception {
        try (var paths = Files.walk(LANG)) {
            for (Path path : (Iterable<Path>) paths.filter(
                file -> file.toString()
                    .endsWith(".lang"))::iterator) {
                try (InputStream stream = Files.newInputStream(path)) {
                    for (var entry : StringTranslate.parseLangFile(stream)
                        .entrySet()) {
                        assertFalse(
                            entry.getKey()
                                .endsWith(".name.name"),
                            path + ": " + entry.getKey());
                        assertFalse(
                            entry.getValue()
                                .matches("(?:item|block|tile|fluid)\\.neoecoae\\.[a-z0-9_.]+"),
                            path + ": untranslated " + entry.getKey());
                    }
                }
            }
        }
    }

    /** The no-stack and stack getters return the same source key, so ".name" is appended exactly once. */
    private static String nameKey(String unlocalized) {
        assertNotNull(unlocalized);
        assertFalse(unlocalized.endsWith(".name"), unlocalized + " would be translated as " + unlocalized + ".name");
        assertFalse(unlocalized.startsWith("tile.neoecoae."), unlocalized + " bypasses the shipped block keys");
        return unlocalized + ".name";
    }

    /** The blocks exactly as the FML registration paths construct them. */
    private static Map<String, Block> blocks() throws Exception {
        Map<String, Block> blocks = new LinkedHashMap<>();
        blocks.putAll(StorageMachines.blocks());
        blocks.putAll(CraftingMachines.blocks());
        for (Block block : Arrays.asList(
            WorkstationMachines.WORKSTATION,
            WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING.value(),
            WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.value(),
            WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH.value(),
            WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH.value()))
            blocks.put(((NativeModelIdentity) block).model(), block);
        // Ordinary blocks copy vanilla prototypes that need the FML block registry, which unit tests cannot boot.
        // Their real classes are allocated without that constructor; the name getters only read the source id.
        for (var value : catalog("core-blocks.json").getAsJsonArray("blocks")) {
            JsonObject definition = value.getAsJsonObject();
            String id = definition.get("id")
                .getAsString();
            blocks.put(
                id,
                shell(
                    definition.get("factory")
                        .getAsString()
                        .equals("frame") ? cn.dancingsnow.neoecoae.blocks.CasingBlock.class
                            : NativeSimpleModelBlock.class,
                    id));
        }
        for (var value : catalog("core-registration.json").getAsJsonArray("blocks")) {
            JsonObject definition = value.getAsJsonObject();
            String id = definition.get("id")
                .getAsString();
            if (!blocks.containsKey(id) && definition.get("registration")
                .getAsString()
                .equals("pending_behavior")) blocks.put(id, shell(NativeRegisteredBlock.class, id));
        }
        return blocks;
    }

    /** The real block class, allocated by Mockito's inline maker without its registry-dependent constructor. */
    private static Block shell(Class<? extends NativeSimpleModelBlock> type, String id) throws Exception {
        NativeBlockProperties properties = mock(NativeBlockProperties.class);
        Field source = NativeBlockProperties.class.getDeclaredField("id");
        source.setAccessible(true);
        source.set(properties, id);
        NativeSimpleModelBlock block = mock(type, CALLS_REAL_METHODS);
        Field field = NativeSimpleModelBlock.class.getDeclaredField("properties");
        field.setAccessible(true);
        field.set(block, properties);
        return block;
    }

    private static Set<String> catalogIds(String file, String kind) throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        for (var value : catalog(file).getAsJsonArray(kind)) {
            JsonObject entry = value.getAsJsonObject();
            if (entry.has("enabled") && !entry.get("enabled")
                .getAsBoolean()) continue;
            ids.add(
                entry.get("id")
                    .getAsString());
        }
        return ids;
    }

    private static JsonObject catalog(String file) throws Exception {
        try (var reader = new InputStreamReader(
            NativeContentTranslationTest.class.getResourceAsStream("/neoecoae/" + file),
            StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        }
    }

    private static Map<String, String> lang(String locale) throws Exception {
        try (InputStream stream = Files.newInputStream(LANG.resolve(locale + ".lang"))) {
            return StringTranslate.parseLangFile(stream);
        }
    }
}
