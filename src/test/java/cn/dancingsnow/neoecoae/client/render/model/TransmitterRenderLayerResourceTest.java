package cn.dancingsnow.neoecoae.client.render.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class TransmitterRenderLayerResourceTest {

    private static final String MODEL_ROOT = "/assets/neoecoae/models/block/";
    private static final String[] TRANSMITTER_MODELS = { "computation_transmitter.json",
        "computation_transmitter_formed.json", "computation_transmitter_formed_l4.json",
        "computation_transmitter_formed_l6.json", "computation_transmitter_formed_l9.json" };

    @Test
    void onlyInnerVerticalGlassFacesUseTheTranslucentPass() throws IOException {
        for (String modelName : TRANSMITTER_MODELS) {
            JsonObject model = readModel(modelName);
            assertEquals(
                "cutout",
                model.get("render_type")
                    .getAsString(),
                modelName);

            int translucentFaceCount = 0;
            for (JsonElement elementValue : model.getAsJsonArray("elements")) {
                JsonObject element = elementValue.getAsJsonObject();
                String elementName = element.has("name") ? element.get("name")
                    .getAsString() : "";
                for (Entry<String, JsonElement> faceEntry : element.getAsJsonObject("faces")
                    .entrySet()) {
                    JsonObject face = faceEntry.getValue()
                        .getAsJsonObject();
                    if (!face.has("render_type")) {
                        continue;
                    }

                    translucentFaceCount++;
                    assertEquals("glass", elementName, modelName + " must not make cavity walls translucent");
                    assertEquals(
                        "translucent",
                        face.get("render_type")
                            .getAsString(),
                        modelName);
                    assertTrue(
                        "north".equals(faceEntry.getKey()) || "south".equals(faceEntry.getKey()),
                        modelName + " must keep horizontal cavity walls opaque");
                }
            }

            int expectedCount = "computation_transmitter.json".equals(modelName) ? 1 : 2;
            assertEquals(expectedCount, translucentFaceCount, modelName);
        }
    }

    private static JsonObject readModel(String modelName) throws IOException {
        try (
            InputStream stream = TransmitterRenderLayerResourceTest.class.getResourceAsStream(MODEL_ROOT + modelName)) {
            assertNotNull(stream, modelName + " must be packaged as a classpath resource");
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new JsonParser().parse(reader)
                    .getAsJsonObject();
            }
        }
    }
}
