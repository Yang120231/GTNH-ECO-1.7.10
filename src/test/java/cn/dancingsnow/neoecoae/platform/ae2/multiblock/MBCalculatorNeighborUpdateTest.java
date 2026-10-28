package cn.dancingsnow.neoecoae.platform.ae2.multiblock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;

/** Neighbor filtering and the recursion guard must match AE2's MBCalculator. */
class MBCalculatorNeighborUpdateTest {

    private static final BlockPos MIN = new BlockPos(0, 0, 0), MAX = new BlockPos(1, 2, 3);
    private final Map<BlockPos, TileEntity> tiles = new HashMap<>();
    private ServerWorldView level;
    private Target target;
    private TestCluster cluster;
    private CountingCalculator calculator;

    @BeforeEach
    void setup() {
        level = mock(ServerWorldView.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockEntity(any()))
            .thenAnswer(invocation -> tiles.get(invocation.getArgument(0, BlockPos.class)));
        target = mock(Target.class);
        when(target.isValid()).thenReturn(true);
        cluster = new TestCluster();
        when(target.getCluster()).thenAnswer(invocation -> cluster);
        calculator = new CountingCalculator(target);
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) tiles.put(pos, member(cluster));
    }

    @AfterEach
    void clearGuard() {
        MBCalculator.setModificationInProgress(null);
    }

    @Test
    void unrelatedNeighborChangeDoesNotRecalculate() {
        BlockPos owner = new BlockPos(1, 1, 3);
        tiles.put(owner.relative(cn.dancingsnow.neoecoae.platform.minecraft.Direction.EAST), mock(TileEntity.class));
        calculator.updateMultiblockAfterNeighborUpdate(level, owner, owner);
        assertEquals(0, calculator.calculations, "A non-part outside the bounds cannot change the structure");

        calculator.updateMultiblockAfterNeighborUpdate(level, owner, new BlockPos(5, 5, 5));
        assertEquals(0, calculator.calculations);
    }

    @Test
    void validPartOutsideTheBoundsRecalculates() {
        BlockPos owner = new BlockPos(1, 1, 3);
        tiles.put(new BlockPos(1, 1, 4), member(null));
        calculator.updateMultiblockAfterNeighborUpdate(level, owner, owner);
        assertEquals(1, calculator.calculations);
    }

    @Test
    void foreignOrMissingTileInsideTheBoundsRecalculates() {
        BlockPos owner = new BlockPos(0, 1, 1);
        tiles.remove(new BlockPos(0, 1, 2));
        calculator.updateMultiblockAfterNeighborUpdate(level, owner, owner);
        assertEquals(1, calculator.calculations);

        tiles.put(new BlockPos(0, 1, 2), member(new TestCluster()));
        calculator.updateMultiblockAfterNeighborUpdate(level, owner, new BlockPos(0, 1, 2));
        assertEquals(2, calculator.calculations);
    }

    @Test
    void unformedPartsAlwaysRecalculate() {
        cluster = null;
        calculator.updateMultiblockAfterNeighborUpdate(level, MIN, MIN);
        assertEquals(1, calculator.calculations);
    }

    @Test
    void memberUpdatesCannotRecursivelyRecalculate() {
        cluster = null;
        calculator.calculateMultiblock(level, MIN);
        assertTrue(calculator.guardHeldDuringUpdate, "AE2 holds the modification guard while members are updated");
        assertEquals(1, calculator.verifications, "The nested calculation must be ignored");
        assertFalse(MBCalculator.isModificationInProgress(), "The guard must be released afterwards");
    }

    @Test
    void destroyedClusterIsNotRebuiltDuringTeardown() {
        cluster.destroyed = true;
        calculator.calculateMultiblock(level, MIN);
        assertEquals(0, calculator.verifications);
    }

    private TileEntity member(TestCluster owner) {
        Part part = mock(Part.class);
        when(part.getCluster()).thenReturn(owner);
        return part;
    }

    abstract static class Part extends TileEntity implements appeng.me.cluster.IAEMultiBlock {
    }

    interface Target extends IAEMultiBlock<TestCluster> {
    }

    static final class TestCluster implements IAECluster {

        boolean destroyed;

        @Override
        public BlockPos getBoundsMin() {
            return MIN;
        }

        @Override
        public BlockPos getBoundsMax() {
            return MAX;
        }

        @Override
        public Iterator<? extends TileEntity> getBlockEntities() {
            return Collections.emptyIterator();
        }

        @Override
        public boolean isDestroyed() {
            return destroyed;
        }

        @Override
        public void updateStatus(boolean updateGrid) {}

        @Override
        public void destroy() {
            destroyed = true;
        }
    }

    private final class CountingCalculator extends MBCalculator<Target, TestCluster> {

        int calculations, verifications;
        boolean guardHeldDuringUpdate;

        CountingCalculator(Target target) {
            super(target);
        }

        @Override
        public void calculateMultiblock(ServerWorldView level, BlockPos location) {
            calculations++;
            super.calculateMultiblock(level, location);
        }

        @Override
        public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
            return true;
        }

        @Override
        public TestCluster createCluster(ServerWorldView level, BlockPos min, BlockPos max) {
            return new TestCluster();
        }

        @Override
        public boolean verifyInternalStructure(ServerWorldView level, BlockPos min, BlockPos max) {
            verifications++;
            return true;
        }

        @Override
        public void updateBlockEntities(TestCluster created, ServerWorldView level, BlockPos min, BlockPos max) {
            guardHeldDuringUpdate = isModificationInProgress();
            cluster = created;
            super.calculateMultiblock(level, min);
        }

        @Override
        public boolean isValidBlockEntity(TileEntity tile) {
            return tile instanceof Part;
        }
    }
}
