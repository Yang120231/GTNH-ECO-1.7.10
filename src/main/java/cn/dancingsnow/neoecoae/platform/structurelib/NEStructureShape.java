package cn.dancingsnow.neoecoae.platform.structurelib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation;
import cn.dancingsnow.neoecoae.multiblock.placement.PlannedBlock;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.Direction;

/**
 * A MultiBlockDefinition compiled into a StructureLib shape for one length, facing and mirror.
 * World offsets come from MultiBlockRotation, the same transform the calculators were written against, and are
 * expressed in a fixed StructureLib frame, so StructureLib's own alignment math never decides block positions.
 */
final class NEStructureShape {

    static final ExtendedFacing FRAME = ExtendedFacing.DEFAULT;
    static final String PIECE = "main";
    // Below StructureLib's reserved navigation range (0xA000+) and clear of its ' ', '~', '+' and '-' markers.
    private static final char FIRST_ELEMENT = '一';

    private static final Map<MultiBlockDefinition, Map<String, NEStructureShape>> CACHE = new WeakHashMap<>();

    final IStructureDefinition<TileEntity> definition;
    final int offsetA;
    final int offsetB;
    final int offsetC;
    final List<NEStructureElement> elements;

    private NEStructureShape(IStructureDefinition<TileEntity> definition, int offsetA, int offsetB, int offsetC,
        List<NEStructureElement> elements) {
        this.definition = definition;
        this.offsetA = offsetA;
        this.offsetB = offsetB;
        this.offsetC = offsetC;
        this.elements = elements;
    }

    static synchronized NEStructureShape of(MultiBlockDefinition definition, int repeats, Direction facing,
        boolean mirrored) {
        return CACHE.computeIfAbsent(definition, key -> new HashMap<>())
            .computeIfAbsent(
                repeats + ":" + facing + ":" + mirrored,
                key -> compile(definition, repeats, facing, mirrored));
    }

    private static NEStructureShape compile(MultiBlockDefinition source, int repeats, Direction facing,
        boolean mirrored) {
        List<PlannedBlock> blocks = source.requirements(repeats);
        List<int[]> positions = new ArrayList<>(blocks.size());
        List<NEStructureElement> elements = new ArrayList<>(blocks.size());
        // The controller sits at ABC (0, 0, 0) and stays outside the shape.
        int minA = 0, minB = 0, minC = 0, maxA = 0, maxB = 0, maxC = 0;
        for (PlannedBlock block : blocks) {
            if (block.relativePos()
                .equals(MultiBlockRotation.CONTROLLER_ANCHOR)) continue;
            BlockPos world = MultiBlockRotation.localToWorld(block.relativePos(), BlockPos.ZERO, facing, mirrored);
            int[] abc = new int[3];
            FRAME.getOffsetABC(new int[] { world.getX(), world.getY(), world.getZ() }, abc);
            positions.add(abc);
            elements.add(
                new NEStructureElement(
                    MultiBlockRotation.rotateState(block.targetState(), facing, mirrored),
                    block.requirement()
                        .transform(state -> MultiBlockRotation.rotateState(state, facing, mirrored)),
                    block.requiredItem()
                        .copy()));
            minA = Math.min(minA, abc[0]);
            minB = Math.min(minB, abc[1]);
            minC = Math.min(minC, abc[2]);
            maxA = Math.max(maxA, abc[0]);
            maxB = Math.max(maxB, abc[1]);
            maxC = Math.max(maxC, abc[2]);
        }
        char[][][] grid = new char[maxC - minC + 1][maxB - minB + 1][maxA - minA + 1];
        for (char[][] layer : grid) for (char[] row : layer) java.util.Arrays.fill(row, ' ');
        StructureDefinition.Builder<TileEntity> builder = StructureDefinition.builder();
        for (int i = 0; i < positions.size(); i++) {
            int[] abc = positions.get(i);
            char key = (char) (FIRST_ELEMENT + i);
            // Later instructions overwrite earlier ones, matching sequential placement of the old builder.
            grid[abc[2] - minC][abc[1] - minB][abc[0] - minA] = key;
            builder.addElement(key, elements.get(i));
        }
        String[][] shape = new String[grid.length][];
        for (int c = 0; c < grid.length; c++) {
            shape[c] = new String[grid[c].length];
            for (int b = 0; b < grid[c].length; b++) shape[c][b] = new String(grid[c][b]);
        }
        builder.addShape(PIECE, shape);
        return new NEStructureShape(builder.build(), -minA, -minB, -minC, elements);
    }

    void hints(TileEntity host, ItemStack trigger) {
        definition.hints(
            host,
            trigger,
            PIECE,
            host.getWorldObj(),
            FRAME,
            host.xCoord,
            host.yCoord,
            host.zCoord,
            offsetA,
            offsetB,
            offsetC);
    }

    void build(TileEntity host, ItemStack trigger) {
        definition.build(
            host,
            trigger,
            PIECE,
            host.getWorldObj(),
            FRAME,
            host.xCoord,
            host.yCoord,
            host.zCoord,
            offsetA,
            offsetB,
            offsetC);
    }

    int survivalBuild(TileEntity host, ItemStack trigger, int budget, ISurvivalBuildEnvironment env) {
        return definition.survivalBuild(
            host,
            trigger,
            PIECE,
            host.getWorldObj(),
            FRAME,
            host.xCoord,
            host.yCoord,
            host.zCoord,
            offsetA,
            offsetB,
            offsetC,
            budget,
            env,
            false);
    }
}
