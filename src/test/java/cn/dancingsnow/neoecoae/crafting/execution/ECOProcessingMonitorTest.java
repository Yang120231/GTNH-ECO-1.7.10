package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachmentRegistry;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItemRegistry;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

class ECOProcessingMonitorTest {

    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void allThousandMachinesContributeWithinBudgetsAndPagingKeepsTheWholeAggregate() {
        try (var fixture = new Fixture()) {
            var monitor = monitor(1000);
            var key = AEItemKey.of(TestItems.STONE);
            ECOProcessingProgress progress = ECOProcessingProgress.EMPTY;
            for (int tick = 0; tick < 40 && progress.machineCount() < 1000; tick++) {
                fixture.tick.set(tick);
                int before = fixture.reads.get();
                progress = monitor.sample(fixture.world, key, tick);
                monitor.sample(fixture.world, key, tick);
                assertTrue(fixture.reads.get() - before <= ECOProcessingMonitor.READS_PER_CPU_TICK);
            }
            assertEquals(1000, progress.machineCount());
            assertEquals(1000, fixture.reads.get());
            assertEquals(.992F, progress.progress(), .00001F);
            var last = progress.page(333);
            assertEquals(1, last.machines().size());
            assertEquals(progress.progress(), last.progress());
            var decoded = ECOProcessingProgress.read(last.write());
            assertEquals(1000, decoded.machineCount());
            assertEquals(last.progress(), decoded.progress());
            assertTrue(progress.page(-1).machines().isEmpty());
            verify(fixture.world, never()).getChunkFromChunkCoords(anyInt(), anyInt());
        }
    }

    @Test
    void differentCpuViewsShareOneServerReadBudget() {
        try (var fixture = new Fixture()) {
            for (int i = 0; i < 12; i++) monitor(100).sample(fixture.world, AEItemKey.of(TestItems.STONE), 0);
            assertEquals(ECOProcessingMonitor.READS_PER_SERVER_TICK, fixture.reads.get());
        }
    }

    @Test
    void destinationsSurviveSaveReloadAndJobCompletionClearsThem() {
        var original = monitor(1000);
        var restored = new ECOProcessingMonitor();
        restored.load(original.save(TestItemRegistry.CONTEXT), TestItemRegistry.CONTEXT);
        assertEquals(original.save(TestItemRegistry.CONTEXT), restored.save(TestItemRegistry.CONTEXT));
        assertEquals(1000, restored.save(TestItemRegistry.CONTEXT).getList("entries", 10)
            .getCompound(0).getList("targets", 10).size());
        restored.clear(null);
        assertTrue(restored.save(TestItemRegistry.CONTEXT).isEmpty());
    }

    @Test
    void attachmentBindingStartsFreshForEveryJobOnTheSameCpu() {
        var id = ResourceLocation.parse("neoecoae:processing_monitor");
        var cpu = mock(ECOCraftingCPU.class);
        var logic = mock(ECOCraftingCPULogic.class);
        var monitor = new ECOProcessingMonitor();
        when(cpu.getLogic()).thenReturn(logic);
        when(logic.getProcessingMonitor()).thenReturn(monitor);
        var first = new ECOCraftingJobContext(cpu, UUID.randomUUID(), null, 1, 1);
        boolean registeredHere = ECOCraftingJobAttachmentRegistry.create(id, first) == null;
        if (registeredHere) ECOProcessingMonitor.initialize();
        try {
            assertSame(monitor, ECOCraftingJobAttachmentRegistry.create(id, first));
            monitor.observe(AEItemKey.of(TestItems.STONE), new Target(0, BlockPos.ZERO));
            assertFalse(monitor.save(TestItemRegistry.CONTEXT).isEmpty());
            var next = new ECOCraftingJobContext(cpu, UUID.randomUUID(), null, 1, 1);
            assertSame(monitor, ECOCraftingJobAttachmentRegistry.create(id, next));
            assertTrue(monitor.save(TestItemRegistry.CONTEXT).isEmpty());
        } finally { if (registeredHere) ECOCraftingJobAttachmentRegistry.unregister(id); }
    }

    private static ECOProcessingMonitor monitor(int count) {
        var monitor = new ECOProcessingMonitor();
        for (int i = 0; i < count; i++) monitor.observe(AEItemKey.of(TestItems.STONE), new Target(0, new BlockPos(i, 64, 0)));
        return monitor;
    }

    public interface NativeCycle {
        Object getMetaTileEntity();
        int getProgress();
        int getMaxProgress();
        boolean isActive();
    }

    public static final class Machine {
        public final ItemStack[] mOutputItems = { new ItemStack(TestItems.STONE) };
        public final int lastParallel = 1;
    }

    private static final class Fixture implements AutoCloseable {
        final World world = mock(World.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final AtomicInteger reads = new AtomicInteger(), tick = new AtomicInteger();
        final MockedStatic<MinecraftServer> servers = mockStatic(MinecraftServer.class);
        Fixture() {
            try {
                var provider = World.class.getDeclaredField("provider");
                provider.setAccessible(true);
                provider.set(world, mock(WorldProvider.class));
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            var chunks = mock(IChunkProvider.class);
            when(world.getChunkProvider()).thenReturn(chunks);
            when(chunks.chunkExists(anyInt(), anyInt())).thenReturn(true);
            servers.when(MinecraftServer::getServer).thenReturn(server);
            when(server.getTickCounter()).thenAnswer(ignored -> tick.get());
            TileEntity first = machine(0), rest = machine(200);
            when(world.getTileEntity(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                reads.incrementAndGet();
                return call.<Integer>getArgument(0) < 8 ? first : rest;
            });
        }
        TileEntity machine(int progress) {
            var tile = mock(TileEntity.class, withSettings().extraInterfaces(NativeCycle.class));
            var cycle = (NativeCycle) tile;
            when(cycle.getMetaTileEntity()).thenReturn(new Machine());
            when(cycle.getProgress()).thenReturn(progress);
            when(cycle.getMaxProgress()).thenReturn(200);
            when(cycle.isActive()).thenReturn(true);
            return tile;
        }
        @Override public void close() { servers.close(); }
    }
}
