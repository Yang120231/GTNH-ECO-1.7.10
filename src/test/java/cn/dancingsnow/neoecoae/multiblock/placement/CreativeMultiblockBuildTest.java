package cn.dancingsnow.neoecoae.multiblock.placement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestBlocks;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.minecraft.ServerWorldView;
import cn.dancingsnow.neoecoae.platform.multiblock.NativePlacementPlatform;

class CreativeMultiblockBuildTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void creativeBuildReturnsBeforePlacementAndCompletesAcrossTicksWithoutConsumingItems() {
        try (Fixture fixture = new Fixture()) {
            fixture.controller.autoBuild(fixture.player);

            assertTrue(fixture.building.get());
            assertTrue(fixture.placed.isEmpty(), "The button action must return without placing blocks");
            verify(fixture.player).closeScreen();
            verify(fixture.host, never()).rebuildAfterBuild();

            fixture.controller.tick(fixture.level);
            assertTrue(fixture.placed.isEmpty());
            fixture.controller.tick(fixture.level);
            assertEquals(1, fixture.placed.size());
            assertTrue(fixture.building.get());
            verify(fixture.host, never()).rebuildAfterBuild();

            fixture.controller.tick(fixture.level);
            assertEquals(1, fixture.placed.size());
            fixture.controller.tick(fixture.level);
            assertEquals(2, fixture.placed.size());
            assertFalse(fixture.building.get());
            verify(fixture.host).rebuildAfterBuild();
            assertEquals(1, fixture.player.inventory.getStackInSlot(0).stackSize);

            fixture.controller.tick(fixture.level);
            assertEquals(2, fixture.placed.size());
            verify(fixture.host, times(1)).rebuildAfterBuild();
        }
    }

    @Test
    void creativeBuildStopsWhenPlayerLeaves() {
        try (Fixture fixture = new Fixture()) {
            fixture.controller.autoBuild(fixture.player);
            fixture.platform.when(() -> NativePlacementPlatform.findPlayer(fixture.playerId))
                .thenReturn(null);

            fixture.controller.tick(fixture.level);

            assertFalse(fixture.building.get());
            assertTrue(fixture.placed.isEmpty());
            verify(fixture.host, never()).rebuildAfterBuild();
        }
    }

    @Test
    void creativeBuildStopsAtDeniedPlacementWithoutContinuingOrReportingCompletion() {
        try (Fixture fixture = new Fixture()) {
            fixture.controller.autoBuild(fixture.player);
            fixture.controller.tick(fixture.level);
            fixture.controller.tick(fixture.level);
            assertEquals(1, fixture.placed.size());
            fixture.platform
                .when(() -> NativePlacementPlatform.mayInteract(eq(fixture.level), eq(fixture.player), any()))
                .thenReturn(false);

            for (int tick = 0; tick < 6; tick++) fixture.controller.tick(fixture.level);

            assertFalse(fixture.building.get());
            assertEquals(1, fixture.placed.size());
            verify(fixture.host, never()).rebuildAfterBuild();
        }
    }

    @Test
    void survivalStillRequiresEnoughMaterialsBeforeStarting() {
        try (Fixture fixture = new Fixture()) {
            fixture.player.capabilities.isCreativeMode = false;

            fixture.controller.autoBuild(fixture.player);
            for (int tick = 0; tick < 6; tick++) fixture.controller.tick(fixture.level);

            assertFalse(fixture.building.get());
            assertTrue(fixture.placed.isEmpty());
            assertEquals(1, fixture.player.inventory.getStackInSlot(0).stackSize);
            verify(fixture.player, never()).closeScreen();
            verify(fixture.host, never()).rebuildAfterBuild();
        }
    }

    private static final class Fixture implements AutoCloseable {

        private final ServerWorldView level = mock(ServerWorldView.class);
        private final EntityPlayerMP player = mock(EntityPlayerMP.class);
        private final UUID playerId = UUID.randomUUID();
        private final MultiBlockBuildController.Host host = mock(MultiBlockBuildController.Host.class);
        private final MultiBlockBuildController controller = new MultiBlockBuildController(host);
        private final AtomicBoolean building = new AtomicBoolean();
        private final Map<BlockPos, BlockState> placed = new HashMap<>();
        private final MockedStatic<NativePlacementPlatform> platform = mockStatic(NativePlacementPlatform.class);

        private Fixture() {
            player.capabilities = new PlayerCapabilities();
            player.capabilities.isCreativeMode = true;
            player.inventory = new InventoryPlayer(player);
            player.inventory.setInventorySlotContents(0, new ItemStack(TestItems.COBBLESTONE));
            when(player.getUniqueID()).thenReturn(playerId);
            BlockState target = BlockState.defaultState(TestBlocks.STONE);
            BlockState empty = BlockState.defaultState(mock(net.minecraft.block.Block.class));
            MultiBlockDefinition definition = mock(MultiBlockDefinition.class);
            when(definition.getExpandMin()).thenReturn(1);
            when(definition.getExpandMax()).thenReturn(1);
            doAnswer(invocation -> {
                MultiBlockPlanContext context = invocation.getArgument(0);
                context.getPlannedBlocks()
                    .add(new PlannedBlock(new BlockPos(2, 1, 0), target, new ItemStack(TestItems.COBBLESTONE)));
                context.getPlannedBlocks()
                    .add(new PlannedBlock(new BlockPos(3, 1, 0), target, new ItemStack(TestItems.COBBLESTONE)));
                return null;
            }).when(definition)
                .createLevel(any());
            when(host.canPlayerInteract(player)).thenReturn(true);
            when(host.getBuildLevel()).thenReturn(level);
            when(host.getBuildPosition()).thenReturn(new BlockPos(0, 64, 0));
            when(host.getBuildState()).thenReturn(target);
            when(host.getBuildDefinition()).thenReturn(definition);
            when(host.getSelectedBuildLength()).thenReturn(1);
            when(host.isBuildInProgress()).thenAnswer(invocation -> building.get());
            doAnswer(invocation -> {
                building.set(invocation.getArgument(0));
                return null;
            }).when(host)
                .setBuildInProgress(anyBoolean());
            when(level.hasChunkAt(any())).thenReturn(true);
            when(level.getBlockState(any()))
                .thenAnswer(invocation -> placed.getOrDefault(invocation.getArgument(0), empty));
            platform.when(() -> NativePlacementPlatform.findPlayer(playerId))
                .thenReturn(player);
            platform.when(() -> NativePlacementPlatform.insideWorldBounds(any()))
                .thenReturn(true);
            platform.when(() -> NativePlacementPlatform.replaceable(eq(level), any(), eq(empty)))
                .thenReturn(true);
            platform.when(() -> NativePlacementPlatform.mayInteract(eq(level), eq(player), any()))
                .thenReturn(true);
            platform.when(() -> NativePlacementPlatform.placeWithPermissionCheck(eq(level), any(), eq(player)))
                .thenAnswer(invocation -> {
                    WorldPlannedBlock block = invocation.getArgument(1);
                    placed.put(block.worldPos(), block.targetState());
                    return true;
                });
        }

        @Override
        public void close() {
            platform.close();
        }
    }
}
