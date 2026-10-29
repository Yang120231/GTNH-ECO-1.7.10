package cn.dancingsnow.neoecoae.platform.structurelib;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.gtnewhorizon.structurelib.StructureLibAPI;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation;
import cn.dancingsnow.neoecoae.multiblock.placement.PlannedBlock;
import cn.dancingsnow.neoecoae.platform.TestBlocks;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;

class NEStructureShapeTest {

    private static final BlockState CASING = BlockState.defaultState(TestBlocks.STONE);
    private static final BlockState DRIVE = BlockState.defaultState(new Block(Material.iron) {});
    private static final BlockPos CONTROLLER = new BlockPos(100, 64, -40);

    @Test
    void structureLibVisitsExactlyTheRotatedDefinitionPositions() {
        MultiBlockDefinition definition = storageLikeDefinition();
        World world = mock(World.class);
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenReturn(true);
        // StructureLibAPI's debug flag lives on the mod class, whose initializer opens an FML channel.
        try (MockedStatic<StructureLibAPI> api = mockStatic(StructureLibAPI.class)) {
            assertVisits(definition, world);
        }
    }

    private static void assertVisits(MultiBlockDefinition definition, World world) {
        for (Direction facing : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST })
            for (boolean mirrored : new boolean[] { false, true }) for (int repeats : new int[] { 1, 3 }) {
                Map<BlockPos, BlockState> expected = new HashMap<>();
                for (PlannedBlock block : definition.requirements(repeats)) {
                    if (block.relativePos()
                        .equals(MultiBlockRotation.CONTROLLER_ANCHOR)) continue;
                    expected.put(
                        MultiBlockRotation.localToWorld(block.relativePos(), CONTROLLER, facing, mirrored),
                        block.targetState());
                }
                Map<BlockPos, BlockState> visited = new HashMap<>();
                NEStructureShape shape = NEStructureShape.of(definition, repeats, facing, mirrored);
                shape.definition.iterate(
                    NEStructureShape.PIECE,
                    world,
                    NEStructureShape.FRAME,
                    CONTROLLER.getX(),
                    CONTROLLER.getY(),
                    CONTROLLER.getZ(),
                    shape.offsetA,
                    shape.offsetB,
                    shape.offsetC,
                    (element, w, x, y, z, a, b, c) -> {
                        assertNull(
                            visited.put(new BlockPos(x, y, z), ((NEStructureElement) element).target()),
                            "visited twice");
                        return true;
                    });
                assertEquals(expected, visited, facing + " mirrored=" + mirrored + " repeats=" + repeats);
            }
    }

    @Test
    void shapesAreCachedPerLengthFacingAndMirror() {
        MultiBlockDefinition definition = storageLikeDefinition();
        assertSame(
            NEStructureShape.of(definition, 2, Direction.EAST, true),
            NEStructureShape.of(definition, 2, Direction.EAST, true));
        assertNotSame(
            NEStructureShape.of(definition, 2, Direction.EAST, true),
            NEStructureShape.of(definition, 2, Direction.EAST, false));
    }

    /** The storage layout: a 3x3x2 core around the controller anchor, a westward body and a closing casing ring. */
    private static MultiBlockDefinition storageLikeDefinition() {
        MultiBlockDefinition definition = mock(MultiBlockDefinition.class);
        when(definition.requirements(anyInt())).thenAnswer(invocation -> {
            int repeats = invocation.getArgument(0);
            List<PlannedBlock> blocks = new ArrayList<>();
            for (int x = 0; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 1; z++)
                blocks.add(planned(new BlockPos(x, y, z), x == 1 && y == 1 && z == 0 ? DRIVE : CASING));
            for (int i = 0; i < repeats; i++) for (int y = 0; y <= 2; y++)
                for (int z = 0; z <= 1; z++) blocks.add(planned(new BlockPos(-1 - i, y, z), z == 0 ? DRIVE : CASING));
            for (int y = 0; y <= 2; y++)
                for (int z = 0; z <= 1; z++) blocks.add(planned(new BlockPos(-1 - repeats, y, z), CASING));
            return blocks;
        });
        return definition;
    }

    private static PlannedBlock planned(BlockPos pos, BlockState state) {
        return new PlannedBlock(pos, state, new ItemStack(TestItems.COBBLESTONE));
    }
}
