package cn.dancingsnow.neoecoae.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StringTranslate;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.platform.ae2.grid.RelativeSide;
import cn.dancingsnow.neoecoae.platform.ae2.storage.CellState;

/** Check the resources the legacy client actually loads, including its pinned AE2 vocabulary. */
class LanguageResourcesTest {

    private static final Path LANG = Paths.get("src/main/resources/assets/neoecoae/lang");
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?[\\d.]*([dfs%])");
    private static final Pattern KEY = Pattern.compile(
        "\"((?:(?:gui|tooltip|category|cell_type|itemGroup|key|upgrade|block|item|tile|fluid|jade|message|container)\\."
            + "|neoecoae\\.(?:configuration\\.|crafting\\.status\\.|unknow_cell_type))[a-zA-Z0-9_.-]*)\"");

    @Test
    void everyLanguageHasValidUtf8UniqueKeysAndMatchingNativeAliases() throws Exception {
        try (var paths = Files.walk(LANG)) {
            for (Path json : paths.filter(
                path -> path.toString()
                    .endsWith(".json"))
                .collect(Collectors.toList())) {
                Map<String, String> strings = json(json);
                String locale = json.getFileName()
                    .toString()
                    .replace(".json", "");
                String[] region = locale.split("_", 2);
                String nativeLocale = region.length == 2
                    ? region[0] + "_" + region[1].toUpperCase(java.util.Locale.ROOT)
                    : locale;
                Path nativeFile = json.resolveSibling(nativeLocale + ".lang");
                Map<String, String> expected = new LinkedHashMap<>();
                strings.forEach((key, value) -> {
                    expected.put(
                        key,
                        value.replace("\r", "")
                            .replace("\n", "\\n"));
                    if ((key.startsWith("item.") || key.startsWith("block.")) && !key.endsWith(".name")) expected.put(
                        key + ".name",
                        value.replace("\r", "")
                            .replace("\n", "\\n"));
                });
                Map<String, String> actual = new LinkedHashMap<>();
                for (String line : Files.readAllLines(nativeFile, StandardCharsets.UTF_8)) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int separator = line.indexOf('=');
                    assertTrue(separator > 0, nativeFile + ": " + line);
                    String key = line.substring(0, separator);
                    assertNull(actual.put(key, line.substring(separator + 1)), nativeFile + ": duplicate " + key);
                }
                assertEquals(expected.size(), actual.size(), nativeFile + ": native key count");
                for (var entry : expected.entrySet())
                    assertEquals(entry.getValue(), actual.get(entry.getKey()), nativeFile + ": " + entry.getKey());
            }
        }
    }

    @Test
    void translatedArgumentsRetainTheirOriginalPositionsAndTypes() throws Exception {
        Map<String, String> english = json(LANG.resolve("en_us.json"));
        try (var paths = Files.walk(LANG)) {
            for (Path path : paths.filter(
                file -> file.toString()
                    .endsWith(".json"))
                .collect(Collectors.toList())) {
                for (var entry : json(path).entrySet()) {
                    if (english.containsKey(entry.getKey())) assertEquals(
                        arguments(english.get(entry.getKey())),
                        arguments(entry.getValue()),
                        path + ": " + entry.getKey());
                }
            }
        }
    }

    @Test
    void sourceAndScreenLiteralKeysExistInTheActualRuntimeResources() throws Exception {
        Map<String, String> available = runtimeEnglish();
        List<String> missing = new ArrayList<>();
        try (var paths = Files.walk(Paths.get("src/main"))) {
            for (Path path : paths.filter(
                file -> file.toString()
                    .endsWith(".java")
                    || file.toString()
                        .contains("screens")
                        && file.toString()
                            .endsWith(".json"))
                .collect(Collectors.toList())) {
                String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                var matcher = KEY.matcher(source);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (key.endsWith(".") || key.endsWith("_")) continue;
                    if (!available.containsKey(key)) missing.add(path + ": " + key);
                }
                var prefix = Pattern.compile("Component.translatable\\(\"([^\"]+)\" \\+ (?:key|suffix)")
                    .matcher(source);
                if (prefix.find()) {
                    var calls = Pattern.compile("(?<![.\\w])text\\(\\s*\"([^\"]+)\"")
                        .matcher(source);
                    while (calls.find()) {
                        if (calls.group(1)
                            .endsWith(".")) continue;
                        String key = prefix.group(1) + calls.group(1);
                        if (!available.containsKey(key)) missing.add(path + ": " + key);
                    }
                }
            }
        }
        assertEquals(Collections.emptyList(), missing);
    }

    @Test
    void contentCatalogsHaveLegacyItemAndBlockDisplayNames() throws Exception {
        Set<String> keys = runtimeEnglish().keySet();
        for (String kind : Arrays.asList("items", "blocks")) {
            String catalog = kind.equals("items") ? "core-content.json" : "core-registration.json";
            try (var reader = new InputStreamReader(
                LanguageResourcesTest.class.getResourceAsStream("/neoecoae/" + catalog),
                StandardCharsets.UTF_8)) {
                var entries = new JsonParser().parse(reader)
                    .getAsJsonObject()
                    .getAsJsonArray(kind);
                for (var value : entries) {
                    var entry = value.getAsJsonObject();
                    if (entry.has("enabled") && !entry.get("enabled")
                        .getAsBoolean()) continue;
                    String key = (kind.equals("items") ? "item." : "block.") + "neoecoae."
                        + entry.get("id")
                            .getAsString()
                        + ".name";
                    assertTrue(keys.contains(key), catalog + ": " + key);
                }
            }
        }
    }

    @Test
    void dynamicEnumsAndUiStatesHaveTranslations() throws Exception {
        Set<String> keys = runtimeEnglish().keySet();
        for (RelativeSide side : RelativeSide.values()) assertTrue(
            keys.contains(
                "gui.neoecoae.relative_side." + side.name()
                    .toLowerCase(java.util.Locale.ROOT)));
        for (CellState state : CellState.values()) assertTrue(
            keys.contains(
                "gui.neoecoae.storage.ui.cell_state." + state.name()
                    .toLowerCase(java.util.Locale.ROOT)));
        for (ECOStorageInterfaceMode mode : ECOStorageInterfaceMode.values())
            assertTrue(keys.contains("gui.neoecoae.storage_interface.mode." + mode.getSerializedName()));
        for (String state : Arrays.asList(
            "controller_formed",
            "build_in_progress",
            "no_definition",
            "conflicts_detected",
            "structure_ready",
            "ready_to_build",
            "not_enough_items")) assertTrue(keys.contains("gui.neoecoae.multiblock.status." + state), state);
        for (String mode : Arrays.asList("overclock", "active_cooling")) for (String state : Arrays.asList("on", "off"))
            assertTrue(keys.contains("gui.neoecoae.crafting." + mode + "." + state));
        for (String state : Arrays.asList("started", "finished", "cancelled"))
            assertTrue(keys.contains("neoecoae.crafting.status." + state));
        for (String label : Arrays.asList("FromStorage", "Crafting", "Scheduled"))
            assertTrue(keys.contains("gui.appliedenergistics2." + label));
    }

    @Test
    void nativeChatCanRenderEveryLocaleWithoutLosingFormatArguments() throws Exception {
        var instance = StringTranslate.class.getDeclaredField("instance");
        instance.setAccessible(true);
        var languageList = StringTranslate.class.getDeclaredField("languageList");
        languageList.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> original = new HashMap<>((Map<String, String>) languageList.get(instance.get(null)));
        try (var paths = Files.list(LANG)) {
            for (Path path : paths.filter(
                file -> file.toString()
                    .endsWith(".lang"))
                .collect(Collectors.toList())) {
                Map<String, String> merged = runtimeEnglish();
                Map<String, String> translated;
                try (InputStream stream = Files.newInputStream(path)) {
                    translated = StringTranslate.parseLangFile(stream);
                }
                merged.putAll(translated);
                StringTranslate.replaceWith(merged);
                for (var entry : translated.entrySet()) {
                    Set<Integer> indexes = arguments(entry.getValue()).keySet();
                    int count = indexes.stream()
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0);
                    Object[] args = new Object[count];
                    for (int index = 0; index < count; index++) args[index] = "ARGUMENT_" + (index + 1) + "_END";
                    String rendered = assertDoesNotThrow(
                        () -> new ChatComponentTranslation(entry.getKey(), args).getUnformattedText(),
                        path + ": " + entry.getKey());
                    for (int index : indexes) assertTrue(
                        rendered.contains(args[index - 1].toString()),
                        path + ": " + entry.getKey() + " lost argument " + index + ": " + rendered);
                }
            }
        } finally {
            StringTranslate.replaceWith(original);
        }
    }

    private static Map<String, String> runtimeEnglish() throws Exception {
        Map<String, String> result = new HashMap<>();
        for (String resource : Arrays.asList("minecraft", "appliedenergistics2", "neoecoae")) {
            try (InputStream stream = LanguageResourcesTest.class
                .getResourceAsStream("/assets/" + resource + "/lang/en_US.lang")) {
                assertNotNull(stream, resource);
                result.putAll(StringTranslate.parseLangFile(stream));
            }
        }
        return result;
    }

    private static Map<String, String> json(Path path) throws Exception {
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertFalse(text.startsWith("\ufeff"), path + ": UTF-8 BOM");
        Map<String, String> result = new LinkedHashMap<>();
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                String value = reader.nextString();
                assertFalse(
                    value.isEmpty() || value.contains("??") || value.indexOf('\ufffd') >= 0,
                    path + ": broken translation " + key);
                assertNull(result.put(key, value), path + ": duplicate " + key);
            }
            reader.endObject();
        }
        return result;
    }

    private static Map<Integer, Set<String>> arguments(String value) {
        Map<Integer, Set<String>> result = new HashMap<>();
        var matcher = FORMAT.matcher(value);
        int next = 0;
        while (matcher.find()) {
            if (matcher.group(2)
                .equals("%")) continue;
            int index = matcher.group(1) == null ? ++next : Integer.parseInt(matcher.group(1));
            result.computeIfAbsent(index, ignored -> new HashSet<>())
                .add(matcher.group(2));
        }
        return result;
    }
}
