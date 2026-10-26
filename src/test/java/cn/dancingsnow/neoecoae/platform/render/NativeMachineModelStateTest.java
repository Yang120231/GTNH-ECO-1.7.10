package cn.dancingsnow.neoecoae.platform.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import cn.dancingsnow.neoecoae.client.render.model.ModelFacing;
import cn.dancingsnow.neoecoae.platform.registry.CraftingMachines;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageMachines;
import cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines;

class NativeMachineModelStateTest {

    @Test
    void everyMachineHasAnInventoryModelAndAPlacedModelInEachOrientation() throws Exception {
        Map<String, Block> blocks = new LinkedHashMap<>();
        blocks.putAll(StorageMachines.blocks());
        blocks.putAll(CraftingMachines.blocks());
        blocks.put("integrated_working_station", WorkstationMachines.WORKSTATION);
        for (var entry : blocks.entrySet()) {
            NativeMachineBlock block = (NativeMachineBlock) entry.getValue();
            String resource = "/assets/neoecoae/blockstates/" + block.model() + ".json";
            var stream = getClass().getResourceAsStream(resource);
            assertNotNull(stream, resource);
            NativeMachineRendering.BlockModels models = new NativeMachineRendering.BlockModels();
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var variants = new JsonParser().parse(reader)
                    .getAsJsonObject()
                    .getAsJsonObject("variants");
                for (var variant : variants.entrySet()) {
                    models.variants.add(
                        new NativeMachineRendering.Variant(
                            variant.getKey(),
                            variant.getValue()
                                .getAsJsonObject()
                                .get("model")
                                .getAsString(),
                            ModelFacing.NORTH));
                }
            }
            assertDoesNotThrow(() -> models.select(block.defaultBlockState()), entry.getKey() + " inventory");
            for (int metadata = 2; metadata <= 5; metadata++) {
                var state = block.fromMetadata(metadata);
                assertDoesNotThrow(() -> models.select(state), entry.getKey() + " metadata=" + metadata);
            }
        }
    }
}
