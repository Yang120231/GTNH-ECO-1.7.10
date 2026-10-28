package cn.dancingsnow.neoecoae.platform.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.renderer.Tessellator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.google.gson.JsonParser;

import cn.dancingsnow.neoecoae.platform.minecraft.Direction;
import cn.dancingsnow.neoecoae.platform.registry.NativeMachineBlock;
import cn.dancingsnow.neoecoae.platform.registry.StorageMachines;

class NativeDriveLedRendererTest {

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = { "NORTH", "EAST", "SOUTH", "WEST" })
    void ledCoversTheHousingSlotOnTheFrontOfEveryOrientation(Direction facing) throws Exception {
        var state = ((NativeMachineBlock) StorageMachines.ECO_DRIVE.value()).defaultBlockState()
            .setValue(NativeMachineBlock.FACING, facing);
        int rotation = NativeDriveLedRenderer.ledRotation(state);
        try (var reader = new InputStreamReader(
            getClass().getResourceAsStream("/assets/neoecoae/blockstates/eco_drive.json"),
            StandardCharsets.UTF_8)) {
            var variant = new JsonParser().parse(reader)
                .getAsJsonObject()
                .getAsJsonObject("variants")
                .getAsJsonObject(
                    "facing=" + facing.name()
                        .toLowerCase(Locale.ROOT));
            assertEquals(
                variant.has("y") ? variant.get("y")
                    .getAsInt() : 0,
                rotation);
        }

        List<double[]> vertices = new ArrayList<>();
        Tessellator tessellator = mock(Tessellator.class);
        doAnswer(call -> {
            vertices.add(new double[] { call.getArgument(0), call.getArgument(1), call.getArgument(2) });
            return null;
        }).when(tessellator)
            .addVertex(anyDouble(), anyDouble(), anyDouble());
        NativeDriveLedRenderer.addLedVertices(tessellator);

        assertEquals(4, vertices.size());
        double[][] slot = { { 3, 11 }, { 4, 11 }, { 4, 13 }, { 3, 13 } };
        double radians = Math.toRadians(-rotation);
        for (int i = 0; i < vertices.size(); i++) {
            double[] vertex = vertices.get(i);
            assertEquals(slot[i][0] / 16.0, vertex[0], 1.0e-9);
            assertEquals(slot[i][1] / 16.0, vertex[1], 1.0e-9);
            // Apply the TESR's rotation about the block center and check the outward-facing plane.
            double x = 0.5 + Math.cos(radians) * (vertex[0] - 0.5) + Math.sin(radians) * (vertex[2] - 0.5);
            double z = 0.5 - Math.sin(radians) * (vertex[0] - 0.5) + Math.cos(radians) * (vertex[2] - 0.5);
            double front = facing == Direction.NORTH ? -z
                : facing == Direction.EAST ? x - 1 : facing == Direction.SOUTH ? z - 1 : -x;
            assertEquals(0.001, front, 1.0e-9, "LED must sit just outside the cell's front face");
        }
    }
}
