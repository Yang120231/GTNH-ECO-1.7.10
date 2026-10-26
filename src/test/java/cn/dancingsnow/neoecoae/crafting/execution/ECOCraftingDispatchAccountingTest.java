package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import cn.dancingsnow.neoecoae.platform.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyTypes;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

class ECOCraftingDispatchAccountingTest {

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private final AEKey output = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final AEKey secondary = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final AEKey container = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final IPatternDetails pattern = mock(IPatternDetails.class);
    private final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class, RETURNS_DEEP_STUBS);
    private final ECOCraftingDispatchAccounting accounting = new ECOCraftingDispatchAccounting(ignored -> {}, () -> {});
    private final ICraftingPlan plan = mock(ICraftingPlan.class);
    private MockedStatic<AEKeyTypes> keyTypes;

    @BeforeEach
    void initializeKeyTypes() {
        keyTypes = mockStatic(AEKeyTypes.class);
    }

    @AfterEach
    void closeKeyTypes() {
        keyTypes.close();
    }

    ECOCraftingDispatchAccountingTest() {
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .listOf(new GenericStack(output, 3), new GenericStack(secondary, 1)));
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 30));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.patternTimes()).thenReturn(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.mapOf(pattern, 10L));
    }

    private ExecutingCraftingJob newJob() {
        return new ExecutingCraftingJob(
            plan,
            ignored -> {},
            new CraftingLink(
                CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false),
                cpu.getCluster()
                    .nativeCPU(cpu)),
            null);
    }

    private void accept(ExecutingCraftingJob job, long crafts) {
        var request = new ECOCraftingDispatchRequest(
            job,
            null,
            pattern,
            new KeyCounter[0],
            new KeyCounter(),
            new KeyCounter(),
            10,
            null,
            null);
        accounting.apply(
            request,
            ECOCraftingDispatchResult.batch(
                crafts,
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                    .listOf(new GenericStack(output, crafts * 3), new GenericStack(secondary, crafts)),
                cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(new GenericStack(container, crafts))),
            () -> {});
    }

    @Test
    void onlyAcceptedMultiCraftOutputsAreMarkedIncludingSecondaryProducts() {
        var job = newJob();
        accept(job, 1);
        assertTrue(job.batchedOutputs.isEmpty());
        accept(job, 4);
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(output, secondary), job.batchedOutputs);
        assertFalse(job.batchedOutputs.contains(container));
        assertEquals(5, job.tasks.get(pattern).value);
        assertEquals(15, job.waitingFor.list.get(output));
        accept(job, 1);
        assertEquals(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(output, secondary), job.batchedOutputs);
    }

    @Test
    void cpuProjectionDoesNotCarryMarkersIntoANewJobOrIdleState() {
        var logic = new ECOCraftingCPULogic(cpu);
        var job = newJob();
        logic.setJobFromPersistence(job);
        assertFalse(logic.isBatchedOutput(output));
        accept(job, 4);
        assertTrue(logic.isBatchedOutput(output));
        assertTrue(logic.isBatchedOutput(secondary));
        assertFalse(logic.isBatchedOutput(container));
        logic.setJobFromPersistence(newJob());
        assertFalse(logic.isBatchedOutput(output));
        logic.setJobFromPersistence(null);
        assertFalse(logic.isBatchedOutput(output));
    }

    @Test
    void checkpointPreservesMarkersAndOlderSavesDefaultToUnmarked() {
        var registries = RegistryAccess.EMPTY;
        var definition = mock(AEItemKey.class);
        when(pattern.getDefinition()).thenReturn(definition);
        when(definition.toTag(registries)).thenAnswer(ignored -> new CompoundTag());
        var outputTag = new CompoundTag();
        outputTag.putString("testKey", "output");
        var secondaryTag = new CompoundTag();
        secondaryTag.putString("testKey", "secondary");
        when(output.toTagGeneric(registries)).thenReturn(outputTag);
        when(secondary.toTagGeneric(registries)).thenReturn(secondaryTag);
        var logic = new ECOCraftingCPULogic(cpu);
        try (var keys = mockStatic(AEItemKey.class);
            var genericKeys = mockStatic(AEKey.class);
            var patterns = mockStatic(PatternDetailsHelper.class);
            var stacks = mockStatic(GenericStack.class, CALLS_REAL_METHODS)) {
            keys.when(() -> AEItemKey.fromTag(eq(registries), any()))
                .thenReturn(definition);
            genericKeys.when(() -> AEKey.fromTagGeneric(eq(registries), any()))
                .thenAnswer(
                    invocation -> ((CompoundTag) invocation.getArgument(1)).getString("testKey")
                        .equals("output") ? output : secondary);
            patterns.when(() -> PatternDetailsHelper.decodePattern(eq(definition), any()))
                .thenReturn(pattern);
            stacks.when(() -> GenericStack.writeTag(eq(registries), any()))
                .thenReturn(new CompoundTag());
            stacks.when(() -> GenericStack.readTag(eq(registries), any()))
                .thenReturn(new GenericStack(output, 30));
            var job = newJob();
            job.batchedOutputs.addAll(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(output, secondary));
            job.fuzzyPlanningItemIds = cn.dancingsnow.neoecoae.platform.jdk.JdkCompat
                .setOf(cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse("minecraft:iron_pickaxe"));
            var saved = job.writeToNBT(registries);
            var restored = new ExecutingCraftingJob(saved, registries, ignored -> {}, logic);
            assertEquals(job.batchedOutputs, restored.batchedOutputs);
            assertEquals(job.fuzzyPlanningItemIds, restored.fuzzyPlanningItemIds);
            saved.remove("batchedOutputs");
            saved.remove("fuzzyPlanningItemIds");
            assertTrue(new ExecutingCraftingJob(saved, registries, ignored -> {}, logic).batchedOutputs.isEmpty());
            assertTrue(
                new ExecutingCraftingJob(saved, registries, ignored -> {}, logic).fuzzyPlanningItemIds.isEmpty());
        }
    }
}
