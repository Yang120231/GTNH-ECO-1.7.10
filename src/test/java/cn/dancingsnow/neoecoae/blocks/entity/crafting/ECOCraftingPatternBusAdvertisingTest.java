package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
import cn.dancingsnow.neoecoae.platform.network.ServerTasks;

/** Pattern bus advertising inside a Network Switch group, slot decode caching and AE2 update coalescing. */
class ECOCraftingPatternBusAdvertisingTest {

    private final World world = mock(World.class);

    /** Real bus with the recipe registry and the live AE2 grid replaced by counters. */
    private static final class TestBus extends ECOCraftingPatternBusBlockEntity {

        final Map<ItemStack, NativePatternDetails> patterns = new IdentityHashMap<>();
        final AtomicInteger events = new AtomicInteger();

        TestBus() {
            super(
                new BlockEntityType<>("test_pattern_bus", () -> null),
                BlockPos.ZERO,
                new BlockState(mock(Block.class), Collections.emptyMap()));
        }

        void world(World value) {
            worldObj = value;
        }

        @Override
        NativePatternDetails decodePattern(ItemStack stack, World world) {
            return patterns.get(stack);
        }

        @Override
        void postPatternChange() {
            events.incrementAndGet();
        }

        /** Stores a pattern stack and returns the native details AE2 would see for it. */
        ICraftingPatternDetails store(int slot) {
            var stack = new ItemStack(TestItems.DIAMOND);
            var nativeDetails = mock(ICraftingPatternDetails.class);
            var details = mock(NativeCraftingPattern.class);
            when(details.nativeDetails()).thenReturn(nativeDetails);
            when(details.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
            when(details.getOutputs()).thenReturn(Collections.emptyList());
            patterns.put(stack, details);
            getInternalInventory().setStackInSlot(slot, stack);
            return nativeDetails;
        }
    }

    @BeforeEach
    void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @AfterEach
    void stopServer() {
        ServerTasks.stopped();
    }

    private static NECraftingCluster host(int index, TestBus bus, ECOCraftingWorkerBlockEntity worker) {
        var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
        var controller = mock(ECOCraftingSystemBlockEntity.class);
        when(controller.getBlockPos()).thenReturn(new BlockPos(index, 0, 0));
        cluster.addBlockEntity(controller);
        when(worker.getCluster()).thenReturn(cluster);
        cluster.addBlockEntity(worker);
        cluster.addBlockEntity(bus);
        bus.updateCluster(cluster);
        return cluster;
    }

    private static NECraftingNetworkCluster group(NECraftingCluster... members) {
        var network = new NECraftingNetworkCluster();
        network.configure(java.util.Arrays.asList(members));
        for (var member : members) member.setNetworkCluster(network);
        return network;
    }

    private static NativeCraftingPattern dispatchable() {
        var pattern = mock(NativeCraftingPattern.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(Collections.emptyList());
        return pattern;
    }

    @Test
    void groupedHostsAdvertiseEachPatternThroughExactlyOneProvider() {
        var busA = new TestBus();
        var busB = new TestBus();
        var a = host(0, busA, mock(ECOCraftingWorkerBlockEntity.class));
        var b = host(1, busB, mock(ECOCraftingWorkerBlockEntity.class));
        var nativeA = busA.store(0);
        var nativeB = busB.store(0);
        group(a, b);
        busA.world(world);
        busB.world(world);

        var helper = mock(ICraftingProviderHelper.class);
        busA.provideCrafting(helper);
        busB.provideCrafting(helper);

        verify(helper, times(1)).addCraftingOption(busA, nativeA);
        verify(helper, times(1)).addCraftingOption(busB, nativeB);
        verify(helper, times(2)).addCraftingOption(any(), any());
        assertEquals(1, busA.getPatternCount());
        assertEquals(1, busB.getPatternCount());
        // The interface/upload pattern area is still the whole group.
        assertEquals(
            2,
            a.getPatternDomainBuses()
                .size());
    }

    @Test
    void unchangedSlotsAreNotDecodedAgain() {
        var bus = new TestBus();
        host(0, bus, mock(ECOCraftingWorkerBlockEntity.class));
        bus.store(0);
        bus.store(1);
        bus.world(world);

        List<IPatternDetails> first = bus.getAvailablePatterns();
        int decoded = bus.catalogForTest()
            .decodeCount();
        assertSame(first, bus.getAvailablePatterns(), "An unchanged bus must reuse its published list");
        bus.provideCrafting(mock(ICraftingProviderHelper.class));
        assertEquals(
            decoded,
            bus.catalogForTest()
                .decodeCount());

        bus.world(null);
        bus.store(5);
        bus.world(world);
        assertEquals(
            3,
            bus.getAvailablePatterns()
                .size());
        assertEquals(
            decoded + 1,
            bus.catalogForTest()
                .decodeCount(),
            "Only the edited slot is decoded");
    }

    @Test
    void manySlotEditsInOneTickPostOnePatternChange() {
        ServerTasks.started();
        var bus = new TestBus();
        for (int slot = 0; slot < 20; slot++) bus.store(slot);
        bus.refreshAdvertisedPatterns();
        bus.getInternalInventory()
            .markDirty();
        assertEquals(0, bus.events.get(), "Changes are published at the end of the tick, not per mutation");

        ServerTasks.tick();
        assertEquals(1, bus.events.get());
        ServerTasks.tick();
        assertEquals(1, bus.events.get());

        bus.store(30);
        ServerTasks.tick();
        assertEquals(2, bus.events.get(), "A later tick's change is published again");
    }

    @Test
    void hostLeavingTheGroupStopsSharingPatternsAndWorkers() {
        var busA = new TestBus();
        var busB = new TestBus();
        var workerA = mock(ECOCraftingWorkerBlockEntity.class);
        var workerB = mock(ECOCraftingWorkerBlockEntity.class);
        var a = host(0, busA, workerA);
        var b = host(1, busB, workerB);
        var nativeA = busA.store(0);
        busB.store(0);
        var network = group(a, b);
        busA.world(world);
        busB.world(world);

        network.configure(Collections.singletonList(b));
        a.setNetworkCluster(null);

        var helper = mock(ICraftingProviderHelper.class);
        busB.provideCrafting(helper);
        verify(helper, never()).addCraftingOption(any(), eq(nativeA));

        when(workerA.pushPattern(any(ECOExtractedPatternExecution.class), any())).thenReturn(true);
        when(workerB.pushPattern(any(ECOExtractedPatternExecution.class), any())).thenReturn(false);
        assertFalse(busB.pushPattern(dispatchable(), new KeyCounter[0]));
        verify(workerA, never()).pushPattern(any(ECOExtractedPatternExecution.class), any());
    }

    @Test
    void pushToOneMemberStillReachesAFreeWorkerOfAnotherMember() {
        var busA = new TestBus();
        var busB = new TestBus();
        var workerA = mock(ECOCraftingWorkerBlockEntity.class);
        var workerB = mock(ECOCraftingWorkerBlockEntity.class);
        var a = host(0, busA, workerA);
        var b = host(1, busB, workerB);
        group(a, b);
        when(workerA.pushPattern(any(ECOExtractedPatternExecution.class), any())).thenReturn(false);
        when(workerB.pushPattern(any(ECOExtractedPatternExecution.class), any())).thenReturn(true);

        assertTrue(busA.pushPattern(dispatchable(), new KeyCounter[0]));
        verify(workerB).pushPattern(any(ECOExtractedPatternExecution.class), any());
    }

    @Test
    void nativeBridgeAttributesSubstitutesByCraftingSlot() {
        var encoded = AEItemKey.of(TestItems.STONE);
        var other = AEItemKey.of(TestItems.DIRT);
        var substitute = AEItemKey.of(TestItems.COBBLESTONE);
        var pattern = mock(NativePatternDetails.class);
        var inputs = new IPatternDetails.IInput[] { input(encoded), input(other) };
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getSparseInputs()).thenReturn(
            new GenericStack[] { new GenericStack(encoded, 1), null, null, null, new GenericStack(other, 1), null, null,
                null, null });
        var keys = new AEKey[9];
        var amounts = new long[9];
        keys[0] = substitute;
        amounts[0] = 1;
        keys[4] = other;
        amounts[4] = 1;

        KeyCounter[] bridged = ECOCraftingPatternBusBlockEntity.bridgeInputs(pattern, true, keys, amounts);

        assertNotNull(bridged);
        assertEquals(1L, bridged[0].get(substitute), "The substitute fills the encoded input of its slot");
        assertEquals(1L, bridged[1].get(other));
    }

    @Test
    void nativeBridgeRejectsUnattributableProcessingInputsInsteadOfDroppingThem() {
        var encoded = AEItemKey.of(TestItems.STONE);
        var pattern = mock(NativePatternDetails.class);
        var inputs = new IPatternDetails.IInput[] { input(encoded) };
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getSparseInputs()).thenReturn(new GenericStack[] { new GenericStack(encoded, 1) });
        // A tunnel-expanded table no longer lines up with the sparse layout, so an unknown key cannot be placed.
        AEKey[] keys = { encoded, AEItemKey.of(TestItems.DIRT) };

        assertNull(ECOCraftingPatternBusBlockEntity.bridgeInputs(pattern, false, keys, new long[] { 1, 1 }));
    }

    private static IPatternDetails.IInput input(AEItemKey key) {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(key, 1) });
        when(input.getMultiplier()).thenReturn(1L);
        return input;
    }
}
