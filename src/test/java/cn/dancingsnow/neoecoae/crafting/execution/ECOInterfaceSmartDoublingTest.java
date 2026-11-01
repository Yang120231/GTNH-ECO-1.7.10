package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.util.IConfigManager;
import appeng.util.ScheduledReason;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.AEProcessingPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

/**
 * Smart doubling for AE2 interfaces (block, part, AE2FC dual interface, P2P tunnel): blocking interfaces get one
 * learned-size push per empty target; crafting locks keep one copy per push.
 */
class ECOInterfaceSmartDoublingTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private final AEItemKey stone = AEItemKey.of(TestItems.STONE);
    private final AEItemKey cobble = AEItemKey.of(TestItems.COBBLESTONE);
    private final AEProcessingPattern pattern = mock(AEProcessingPattern.class);
    private final ListCraftingInventory inventory = new ListCraftingInventory(key -> {});
    private final IEnergyGrid energy = mock(IEnergyGrid.class);

    /**
     * DualityInterface cannot initialize outside Minecraft, so the interface is a plain medium whose provider reports
     * the interface capabilities NativeCraftingProvider derives from {@code instanceof DualityInterface}.
     */
    private static ICraftingMedium anInterface(ICraftingMedium.BlockingMode mode) {
        ICraftingMedium medium = mock(ICraftingMedium.class);
        when(medium.getBlockingMode()).thenReturn(mode);
        when(medium.getScheduledReason()).thenReturn(ScheduledReason.UNDEFINED);
        return medium;
    }

    private NativeCraftingProvider interfaceProvider(ICraftingMedium medium) {
        var provider = spy(new NativeCraftingProvider(medium, JdkCompat.listOf(pattern)));
        doReturn(true).when(provider)
            .supportsScaledProcessing();
        doReturn(true).when(provider)
            .allowsScaledWhileBlocking();
        doReturn(true).when(provider)
            .isScaledProcessingFullyInserted();
        return provider;
    }

    @Test
    void everyCraftingLockModeDisablesScaling() {
        assertFalse(NativeCraftingProvider.isCraftingLocked(null));
        assertFalse(NativeCraftingProvider.isCraftingLocked(config(LockCraftingMode.NONE)));
        for (var lock : new LockCraftingMode[] { LockCraftingMode.LOCK_UNTIL_PULSE, LockCraftingMode.LOCK_WHILE_HIGH,
            LockCraftingMode.LOCK_WHILE_LOW, LockCraftingMode.LOCK_UNTIL_RESULT }) {
            assertTrue(NativeCraftingProvider.isCraftingLocked(config(lock)), lock.name());
        }
    }

    private static IConfigManager config(LockCraftingMode lock) {
        IConfigManager config = mock(IConfigManager.class);
        doReturn(lock).when(config)
            .getSetting(Settings.LOCK_CRAFTING_MODE);
        return config;
    }

    @Test
    void blockingInterfaceEntersTheScaledLane() {
        var provider = interfaceProvider(anInterface(ICraftingMedium.BlockingMode.BLOCKING));
        assertTrue(ECOProcessingPatternDispatcher.scaledTransportAllowed(provider));
        assertTrue(provider.isSinglePushPerVisit());
        var smart = interfaceProvider(anInterface(ICraftingMedium.BlockingMode.SMART_BLOCKING));
        assertFalse(smart.isSinglePushPerVisit());
    }

    /** TST's Ultimate ME IO and other foreign mediums count rounds per push; they must never be scaled. */
    @Test
    void foreignBlockingMediumsStayOneCopy() {
        ICraftingMedium foreign = mock(ICraftingMedium.class);
        when(foreign.getBlockingMode()).thenReturn(ICraftingMedium.BlockingMode.BLOCKING);
        var provider = new NativeCraftingProvider(foreign, JdkCompat.listOf(pattern));
        assertFalse(provider.supportsScaledProcessing());
        assertFalse(ECOProcessingPatternDispatcher.scaledTransportAllowed(provider));
    }

    @Test
    void blockingInterfaceDoublesOncePerEmptyTarget() {
        var h = new Harness(anInterface(ICraftingMedium.BlockingMode.BLOCKING));
        assertEquals(JdkCompat.listOf(1L), h.visit(1));
        assertEquals(JdkCompat.listOf(2L), h.visit(20));
        assertEquals(JdkCompat.listOf(4L), h.visit(40));
        assertEquals(JdkCompat.listOf(8L), h.visit(60));
    }

    @Test
    void occupiedTargetRejectionDoesNotShrinkTheLearnedSize() {
        var medium = anInterface(ICraftingMedium.BlockingMode.BLOCKING);
        var h = new Harness(medium);
        h.visit(1);
        h.visit(20);
        assertEquals(JdkCompat.listOf(4L), h.visit(40));

        h.accept = false;
        when(medium.getScheduledReason()).thenReturn(ScheduledReason.BLOCKING_MODE);
        assertEquals(JdkCompat.listOf(8L), h.visit(60));
        assertTrue(h.dispatcher.isScaledFallbackDeferred(h.request, h.provider));

        h.accept = true;
        when(medium.getScheduledReason()).thenReturn(ScheduledReason.UNDEFINED);
        assertEquals(JdkCompat.listOf(8L), h.visit(80));
    }

    @Test
    void smartBlockingInterfaceGrowsWithinOneVisit() {
        var h = new Harness(anInterface(ICraftingMedium.BlockingMode.SMART_BLOCKING));
        var offers = h.visit(1);
        assertTrue(offers.size() > 1, offers.toString());
        assertEquals(1L, offers.get(0));
        assertEquals(2L, offers.get(1));
    }

    /** One CPU-side dispatcher driving 1000 requested copies of 2 stone -> 1 cobblestone. */
    private final class Harness {

        final NativeCraftingProvider provider;
        final ECOProcessingPatternDispatcher dispatcher;
        final ECOCraftingDispatchRequest request;
        boolean accept = true;

        Harness(ICraftingMedium medium) {
            IPatternDetails.IInput input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(stone, 2) });
            when(input.getMultiplier()).thenReturn(2L);
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
            when(pattern.getOutputs()).thenReturn(JdkCompat.listOf(new GenericStack(cobble, 1)));
            when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(cobble, 1));
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(TestItems.BRICK));
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            inventory.insert(stone, 2000, Actionable.MODULATE);
            when(energy.extractAEPower(anyDouble(), any(), eq(PowerMultiplier.CONFIG)))
                .thenAnswer(call -> call.getArgument(0));

            ICraftingPlan plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(cobble, 1000));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            when(plan.patternTimes()).thenReturn(JdkCompat.mapOf(pattern, 1000L));
            var cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
            var job = new ExecutingCraftingJob(
                plan,
                ignored -> {},
                new CraftingLink(
                    CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false),
                    cpu.getCluster()
                        .nativeCPU(cpu)),
                null);
            var inputs = new KeyCounter();
            inputs.add(stone, 2);
            var outputs = new KeyCounter();
            outputs.add(cobble, 1);
            request = new ECOCraftingDispatchRequest(
                job,
                null,
                pattern,
                new KeyCounter[] { inputs },
                outputs,
                new KeyCounter(),
                1000,
                inventory,
                mock(net.minecraft.world.World.class));
            provider = interfaceProvider(medium);
            var accounting = new ECOCraftingDispatchAccounting(
                ignored -> {},
                () -> {},
                ignored -> mock(ECOCraftingJobContext.class),
                event -> {});
            dispatcher = new ECOProcessingPatternDispatcher(
                mock(ECOCraftingCPULogic.class),
                new ECOCraftingEnergyTransaction(() -> {}, () -> 1),
                accounting);
        }

        List<Long> visit(long tick) {
            dispatcher.beginTick(tick);
            List<Long> offers = new ArrayList<>();
            dispatcher.tryScaledDispatch(request, provider, 2.0, energy, ignored -> {}, (scaled, target) -> {
                offers.add(scaled.allowedCrafts());
                return accept;
            });
            return offers;
        }
    }
}
