package cn.dancingsnow.neoecoae.platform.render;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class NativeResourcePackTest {

    @Test
    void bundledPackDeclaresAFormatThatAllowsGtnhLibBlockstates() throws Exception {
        // GTNHLib 0.11.52 rejects JAR blockstates when metadata is absent or pack_format < 4.
        // A loose development resource folder does not exercise that FileResourcePack guard.
        try (var stream = getClass().getResourceAsStream("/pack.mcmeta")) {
            assertNotNull(stream, "The mod JAR needs root pack.mcmeta for GTNHLib blockstate loading");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var pack = new JsonParser().parse(reader)
                    .getAsJsonObject()
                    .getAsJsonObject("pack");
                assertNotNull(pack, "Missing pack metadata");
                assertTrue(pack.has("pack_format"), "Missing pack_format");
                assertTrue(
                    pack.get("pack_format")
                        .getAsInt() >= 4,
                    "GTNHLib requires pack_format >= 4");
                assertTrue(pack.has("description"), "Missing resource pack description");
            }
        }
    }
}
