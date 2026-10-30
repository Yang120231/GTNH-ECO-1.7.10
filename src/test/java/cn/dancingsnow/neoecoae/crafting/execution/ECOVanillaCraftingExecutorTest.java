package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.inv.MEInventoryCrafting;
import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.TestItems;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.NativePatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** The executor for AE2-planned jobs must reproduce GTNH CraftingCPUCluster#executeCrafting. */
class ECOVanillaCraftingExecutorTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private final AEItemKey stone = AEItemKey.of(TestItems.STONE);
    private final AEItemKey cobble = AEItemKey.of(TestItems.COBBLESTONE);
    private final ICraftingPatternDetails details = mock(ICraftingPatternDetails.class);
    private final NativePatternDetails pattern = mock(NativePatternDetails.class);
    private final ListCraftingInventory inventory = new ListCraftingInventory(key -> {});
    private final ECOCraftingCPULogic host = mock(ECOCraftingCPULogic.class);
    private List<ICraftingMedium> mediums = new ArrayList<>();
    private final Set<ICraftingMedium> fakeInterfaces = new HashSet<>();
    private final IEnergyGrid energy = mock(IEnergyGrid.class);
    private final World world = mock(World.class);
    private final ECOVanillaCraftingExecutor executor = new ECOVanillaCraftingExecutor(
        host,
        new ECOVanillaCraftingExecutor.MediumTraits() {

            @Override
            public boolean isFakeCraftingMode(ICraftingMedium medium) {
                return fakeInterfaces.contains(medium);
            }

            @Override
            public int patternCapacityUpgrades(ICraftingMedium medium) {
                return 0;
            }

            @Override
            public MEInventoryCrafting newTable(int width, int height) {
                return table(width * height);
            }
        });

    /** Array-backed MEInventoryCrafting; the real one reaches AE2's Platform, which needs FML. */
    private static MEInventoryCrafting table(int size) {
        MEInventoryCrafting table = mock(MEInventoryCrafting.class);
        IAEStack<?>[] slots = new IAEStack<?>[size];
        when(table.getSizeInventory()).thenReturn(size);
        doAnswer(call -> {
            slots[(int) call.getArgument(0)] = call.getArgument(1);
            return null;
        }).when(table)
            .setInventorySlotContents(anyInt(), any(IAEStack.class));
        when(table.getAEStackInSlot(anyInt())).thenAnswer(call -> slots[(int) call.getArgument(0)]);
        return table;
    }

    private ExecutingCraftingJob job;

    /** Processing pattern 2 stone -> 1 cobblestone, requested 5 times. */
    private void setUp(long crafts, ICraftingMedium... mediums) {
        IAEStack<?>[] inputs = { nativeStack(TestItems.STONE, 2) };
        IAEStack<?>[] outputs = { nativeStack(TestItems.COBBLESTONE, 1) };
        when(details.isCraftable()).thenReturn(false);
        when(details.getCondensedAEInputs()).thenReturn(inputs);
        when(details.getAEInputs()).thenReturn(inputs);
        when(details.getCondensedAEOutputs()).thenReturn(outputs);
        when(details.getAEOutputs()).thenReturn(outputs);
        when(pattern.nativeDetails()).thenReturn(details);
        when(pattern.getOutputs())
            .thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(cobble, 1)));

        ICraftingPlan plan = mock(ICraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(cobble, crafts));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.patternTimes()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, crafts));
        var cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
        job = new ExecutingCraftingJob(
            plan,
            ignored -> {},
            new CraftingLink(
                CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false),
                cpu.getCluster()
                    .nativeCPU(cpu)),
            null);
        job.vanillaExecution = true;

        when(host.getInventory()).thenReturn(inventory);
        when(host.getJob()).thenAnswer(call -> job);
        this.mediums = Arrays.asList(mediums);
        when(energy.extractAEPower(anyDouble(), any(), eq(PowerMultiplier.CONFIG)))
            .thenAnswer(call -> call.getArgument(0));
    }

    /** Stateful stand-in for AE2's AEItemStack, which cannot initialize without FML's LaunchClassLoader. */
    private static IAEItemStack nativeStack(net.minecraft.item.Item item, long size) {
        IAEItemStack stack = mock(IAEItemStack.class);
        long[] amount = { size };
        when(stack.getItem()).thenReturn(item);
        when(stack.getItemDamage()).thenReturn(0);
        when(stack.getTagCompound()).thenReturn(null);
        when(stack.getAmountPerUnit()).thenReturn(1);
        when(stack.getStackSize()).thenAnswer(call -> amount[0]);
        when(stack.setStackSize(anyLong())).thenAnswer(call -> {
            amount[0] = call.getArgument(0);
            return stack;
        });
        when(stack.copy()).thenAnswer(call -> nativeStack(item, amount[0]));
        return stack;
    }

    private List<ICraftingMedium> mediumsOf(ICraftingPatternDetails pattern) {
        return pattern == details ? mediums : new ArrayList<>();
    }

    private static ICraftingMedium accepting() {
        ICraftingMedium medium = mock(ICraftingMedium.class);
        when(medium.pushPattern(any(), any())).thenReturn(true);
        when(medium.getBlockingMode()).thenReturn(ICraftingMedium.BlockingMode.NONE);
        return medium;
    }

    @Test
    void pushesEveryAffordableCopyAcrossMediumsAndAwaitsOutputs() {
        var first = accepting();
        var second = accepting();
        setUp(5, first, second);
        inventory.insert(stone, 6, Actionable.MODULATE);

        executor.tick(job, energy, this::mediumsOf, null, world, 100);

        // Six stone affords three copies; the medium list is walked round-robin as natively.
        verify(first, times(2)).pushPattern(eq(details), any(InventoryCrafting.class));
        verify(second, times(1)).pushPattern(eq(details), any(InventoryCrafting.class));
        assertEquals(2, job.tasks.get(pattern).value);
        assertEquals(0, inventory.list.get(stone));
        assertEquals(3, job.waitingFor.list.get(cobble));
        // Native power: one unit per item, charged once per accepted push.
        verify(energy, times(3)).extractAEPower(2.0, Actionable.MODULATE, PowerMultiplier.CONFIG);
    }

    @Test
    void rejectedPushReturnsTheExtractedInputs() {
        ICraftingMedium full = mock(ICraftingMedium.class);
        when(full.pushPattern(any(), any())).thenReturn(false);
        setUp(5, full);
        inventory.insert(stone, 6, Actionable.MODULATE);

        executor.tick(job, energy, this::mediumsOf, null, world, 100);

        assertEquals(6, inventory.list.get(stone));
        assertEquals(5, job.tasks.get(pattern).value);
        assertEquals(0, job.waitingFor.list.get(cobble));
    }

    @Test
    void blockingMediumBecomesBusyAfterOnePush() {
        ICraftingMedium blocking = mock(ICraftingMedium.class);
        var pushed = new boolean[1];
        when(blocking.pushPattern(any(), any())).thenAnswer(call -> pushed[0] = true);
        when(blocking.isBusy()).thenAnswer(call -> pushed[0]);
        when(blocking.getBlockingMode()).thenReturn(ICraftingMedium.BlockingMode.BLOCKING);
        setUp(5, blocking);
        inventory.insert(stone, 10, Actionable.MODULATE);

        executor.tick(job, energy, this::mediumsOf, null, world, 100);

        verify(blocking, times(1)).pushPattern(any(), any());
        assertEquals(4, job.tasks.get(pattern).value);
    }

    @Test
    void operationWindowMatchesNativeThreeTickHistory() {
        var medium = accepting();
        setUp(5, medium);
        inventory.insert(stone, 100, Actionable.MODULATE);

        // No co-processor: accelerator + 1 - (last three ticks) operations, exactly as natively.
        int[] pushesPerTick = new int[5];
        for (int tick = 0; tick < pushesPerTick.length; tick++) {
            long before = job.tasks.get(pattern).value;
            executor.tick(job, energy, this::mediumsOf, null, world, 0);
            pushesPerTick[tick] = (int) (before - job.tasks.get(pattern).value);
        }
        assertArrayEquals(new int[] { 1, 0, 0, 0, 1 }, pushesPerTick);
    }

    @Test
    void fakeCraftingInterfaceCompletesTheFinalPatternWithoutAwaitingIt() {
        var fake = accepting();
        fakeInterfaces.add(fake);
        setUp(2, fake);
        inventory.insert(stone, 4, Actionable.MODULATE);

        executor.tick(job, energy, this::mediumsOf, null, world, 100);

        assertTrue(job.vanillaFakeCrafting);
        assertEquals(0, job.tasks.get(pattern).value);
        assertEquals(0, job.remainingAmount);
        assertEquals(0, job.waitingFor.list.get(cobble));
    }

    @Test
    void missingInputsNeverReachAMedium() {
        var medium = accepting();
        setUp(5, medium);
        inventory.insert(stone, 1, Actionable.MODULATE);

        executor.tick(job, energy, this::mediumsOf, null, world, 100);

        verify(medium, never()).pushPattern(any(), any());
        assertEquals(1, inventory.list.get(stone));
    }
}
