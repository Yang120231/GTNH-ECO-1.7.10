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
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.energy.IEnergyGrid;
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

/** GT's ME crafting input hatches store any push whole, so ECO offers every affordable copy at once. */
class ECOUnboundedHatchDispatchTest {

    private static final String GT_HATCH = "gregtech.common.tileentities.machines.MTEHatchCraftingInputME";

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private final AEItemKey stone = AEItemKey.of(TestItems.STONE);
    private final AEItemKey cobble = AEItemKey.of(TestItems.COBBLESTONE);
    private final AEProcessingPattern pattern = mock(AEProcessingPattern.class);
    private final ListCraftingInventory inventory = new ListCraftingInventory(key -> {});
    private final IEnergyGrid energy = mock(IEnergyGrid.class);

    @SuppressWarnings("unchecked")
    private static ICraftingMedium gtHatch() throws ClassNotFoundException {
        var medium = (ICraftingMedium) mock(
            (Class<Object>) Class.forName(GT_HATCH, false, ECOUnboundedHatchDispatchTest.class.getClassLoader()));
        when(medium.getBlockingMode()).thenReturn(ICraftingMedium.BlockingMode.NONE);
        return medium;
    }

    private static ICraftingMedium boundedMedium() {
        ICraftingMedium medium = mock(ICraftingMedium.class);
        when(medium.getBlockingMode()).thenReturn(ICraftingMedium.BlockingMode.NONE);
        return medium;
    }

    @Test
    void gtCraftingInputHatchIsRecognizedAsUnbounded() throws ClassNotFoundException {
        var hatch = new NativeCraftingProvider(gtHatch(), JdkCompat.listOf(pattern));
        assertTrue(hatch.isUnboundedPatternHatch());
        assertTrue(hatch.supportsScaledProcessing());
        assertTrue(hatch.isScaledProcessingFullyInserted());

        var other = new NativeCraftingProvider(boundedMedium(), JdkCompat.listOf(pattern));
        assertFalse(other.isUnboundedPatternHatch());
        assertFalse(other.supportsScaledProcessing());
    }

    @Test
    void hatchReceivesEveryAffordableCopyInOnePush() throws ClassNotFoundException {
        assertEquals(JdkCompat.listOf(1000L), firstVisitOffers(gtHatch()));
    }

    /** Native interfaces keep ECO's probing ramp: their send buffer is bounded. */
    @Test
    void boundedTargetsStillStartFromOneCopy() {
        var offers = firstVisitOffers(boundedMedium(), true);
        assertEquals(1L, offers.get(0));
    }

    private List<Long> firstVisitOffers(ICraftingMedium medium) {
        return firstVisitOffers(medium, false);
    }

    /** Runs one scaled visit for 1000 requested copies of 2 stone -> 1 cobblestone with stock for all of them. */
    private List<Long> firstVisitOffers(ICraftingMedium medium, boolean forceScaled) {
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
        var request = new ECOCraftingDispatchRequest(
            job,
            null,
            pattern,
            new KeyCounter[] { inputs },
            outputs,
            new KeyCounter(),
            1000,
            inventory,
            mock(net.minecraft.world.World.class));

        var provider = spy(new NativeCraftingProvider(medium, JdkCompat.listOf(pattern)));
        if (forceScaled) doReturn(true).when(provider)
            .supportsScaledProcessing();
        var accounting = new ECOCraftingDispatchAccounting(
            ignored -> {},
            () -> {},
            ignored -> mock(ECOCraftingJobContext.class),
            event -> {});
        var dispatcher = new ECOProcessingPatternDispatcher(
            mock(ECOCraftingCPULogic.class),
            new ECOCraftingEnergyTransaction(() -> {}, () -> 1),
            accounting);
        dispatcher.beginTick(1);
        List<Long> offers = new ArrayList<>();
        dispatcher.tryScaledDispatch(request, provider, 2.0, energy, ignored -> {}, (scaled, target) -> {
            offers.add(scaled.allowedCrafts());
            return true;
        });
        return offers;
    }
}
