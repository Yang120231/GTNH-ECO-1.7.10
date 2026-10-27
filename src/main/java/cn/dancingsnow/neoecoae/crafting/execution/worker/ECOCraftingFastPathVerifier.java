package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingStateSlots;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOReusableStateAnalyzer;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOReusableStateModel;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.minecraft.crafting.TransientCraftingContainer;

/** Produces one verified worker input/output snapshot; it never mutates thread lifecycle state. */
final class ECOCraftingFastPathVerifier {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");

    private final ECOCraftingWorkerRuntimeHost worker;
    private final TransientCraftingContainer craftingInventory;

    ECOCraftingFastPathVerifier(ECOCraftingWorkerRuntimeHost worker, TransientCraftingContainer craftingInventory) {
        this.worker = worker;
        this.craftingInventory = craftingInventory;
    }

    @Nullable
    PreparedWork prepare(ECOExtractedPatternExecution execution, long tick) {
        if (!execution.canUseFastPath()) return slow(execution, false, tick, execution.fastPathReason());

        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        var lookup = cache.lookup(execution, tick, PatternRuntime.reloadGeneration());
        return switch (lookup.status()) {
            case NEGATIVE -> slow(execution, false, tick, lookup.reason() == null ? "NEGATIVE_CACHE" : lookup.reason());
            case MISMATCH -> slow(
                execution,
                true,
                tick,
                lookup.reason() == null ? "CACHE_RESULT_MISMATCH" : lookup.reason());
            case VERIFIED -> fromVerified(execution, lookup.recipe(), tick);
            default -> slow(execution, true, tick, lookup.reason() == null ? "CACHE_MISS" : lookup.reason());
        };
    }

    private PreparedWork fromVerified(ECOExtractedPatternExecution execution, ECOVerifiedFastPathRecipe recipe,
        long tick) {
        if (recipe.hasFluidInput()) return slow(execution, false, tick, "FLUID_INPUT_SINGLE_CRAFT");
        var output = ECOFastPathStacks.toSingleItemStack(recipe.outputsPerCraft());
        var inputs = ECOFastPathStacks.toItemStacks(recipe.inputsPerCraft());
        var remaining = ECOFastPathStacks.toItemStacks(recipe.remainingPerCraft());
        if (!output.isPresent() || !inputs.isPresent() || !remaining.isPresent()) {
            worker.getFastPathCache()
                .putNegative(execution.key(), tick, "CACHED_RESULT_MATERIALIZATION_FAILED");
            return slow(execution, false, tick, "CACHED_RESULT_MATERIALIZATION_FAILED");
        }
        return new PreparedWork(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output.get()),
            inputs.get(),
            remaining.get(),
            "FAST_PATH_HIT");
    }

    @Nullable
    private PreparedWork slow(ECOExtractedPatternExecution execution, boolean verify, long tick, String initialReason) {
        IMolecularAssemblerSupportedPattern pattern = execution.molecularPattern();
        if (pattern == null) return null;
        craftingInventory.clearContent();
        // Pattern materialization is speculative. NativeCraftingPattern consumes one entry from each
        // counter while it maps the sparse AE2 inputs into the physical 3x3 grid. The execution context
        // is also reused by the ordinary fallback when this probe rejects the fast path, so passing its
        // shared counters directly would erase the inputs before the fallback can dispatch them.
        pattern.fillCraftingGrid(copyCraftingContainer(execution.craftingContainer()), craftingInventory::setItem);
        List<ItemStack> beforeSlots = snapshotSlots();
        var positionedInput = craftingInventory.asPositionedCraftInput();
        ItemStack output = pattern.assemble(positionedInput.input(), worker.getNativeLevel());
        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(output)) {
            craftingInventory.clearContent();
            return null;
        }
        List<ItemStack> remainingSlots = ECOCraftingStateSlots.expandRemainingItems(
            positionedInput,
            pattern.getRemainingItems(positionedInput.input()),
            craftingInventory.getWidth(),
            craftingInventory.getHeight());
        List<ItemStack> remaining = remainingSlots.stream()
            .filter(stack -> !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack))
            .map(ItemStack::copy)
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
        List<ItemStack> inputs = snapshotInputs();
        String reason = verify ? verifyAndCache(execution, output, inputs, remaining, beforeSlots, remainingSlots, tick)
            : initialReason;
        return new PreparedWork(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(output.copy()),
            inputs,
            remaining,
            reason);
    }

    private static cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter[] copyCraftingContainer(
        cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter[] source) {
        cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter[] copy = new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? new cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter()
                : source[index].copy();
        }
        return copy;
    }

    private String verifyAndCache(ECOExtractedPatternExecution execution, ItemStack output, List<ItemStack> inputs,
        List<ItemStack> remaining, List<ItemStack> beforeSlots, List<ItemStack> remainingSlots, long tick) {
        var key = execution.key();
        if (key == null) return "KEY_BUILD_FAILED";
        var cache = worker.getFastPathCache();
        var outputs = ECOFastPathStacks.fromItemStack(output);
        var materializedInputs = ECOFastPathStacks.fromItemStacks(inputs);
        var remainders = ECOFastPathStacks.fromItemStacks(remaining);
        boolean fluid = execution.inputItems()
            .stream()
            .anyMatch(stack -> stack.what() instanceof AEFluidKey);
        List<GenericStack> inputEntries = fluid ? execution.inputItems()
            : materializedInputs.orElse(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());
        if (!outputs.isPresent() || inputEntries.isEmpty()) {
            return negative(cache, key, tick, "VERIFIED_OUTPUT_OR_INPUT_CONVERSION_FAILED");
        }
        if (!outputs.get()
            .equals(execution.expectedOutputs())
            || !remainders.get()
                .equals(execution.expectedContainerItems())
            || !fluid && !inputEntries.equals(execution.inputItems())) {
            return negative(cache, key, tick, "ASSEMBLY_CONTRACT_MISMATCH");
        }
        var analysis = ECOReusableStateAnalyzer.analyze(
            beforeSlots,
            remainingSlots,
            execution.fastPathType() == ECORecipeClassifier.Type.DURABILITY_MUTATION);
        if (analysis.rejected()) {
            if ("STATE_SLOT_COUNT_MISMATCH".equals(analysis.rejectReason())) {
                LOGGER.warn(
                    "Fast path state slot mismatch: expected={} actual={}",
                    formatSlots(beforeSlots),
                    formatSlots(remainingSlots));
            }
            return negative(cache, key, tick, analysis.rejectReason());
        }
        if (!verifySecondStateStep(execution, output, beforeSlots, remainingSlots, analysis.model())) {
            return negative(cache, key, tick, "STATE_SECOND_STEP_PROOF_FAILED");
        }
        cache.putPositive(key, outputs.get(), remainders.get(), inputEntries, tick, analysis.model());
        return "CACHE_MISS";
    }

    private boolean verifySecondStateStep(ECOExtractedPatternExecution execution, ItemStack firstOutput,
        List<ItemStack> initialSlots, List<ItemStack> firstRemainingSlots, @Nullable ECOReusableStateModel model) {
        if (model == null || !model.requiresSecondStepProof()) return true;
        IMolecularAssemblerSupportedPattern pattern = execution.molecularPattern();
        if (pattern == null || initialSlots.size() != firstRemainingSlots.size()) return false;
        try {
            for (int slot = 0; slot < initialSlots.size(); slot++) {
                ItemStack initial = initialSlots.get(slot);
                ItemStack remainder = firstRemainingSlots.get(slot);
                craftingInventory.setItem(
                    slot,
                    !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(initial)
                        && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(remainder)
                        && cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItem(initial, remainder)
                            ? cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copy(remainder)
                            : cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copy(initial));
            }
            var input = craftingInventory.asPositionedCraftInput();
            ItemStack secondOutput = pattern.assemble(input.input(), worker.getNativeLevel());
            if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(secondOutput)
                || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(secondOutput)
                    != cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(firstOutput)
                || !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks
                    .isSameItemSameComponents(firstOutput, secondOutput))
                return false;
            List<ItemStack> secondRemaining = ECOCraftingStateSlots.expandRemainingItems(
                input,
                pattern.getRemainingItems(input.input()),
                craftingInventory.getWidth(),
                craftingInventory.getHeight());
            var second = ECOReusableStateAnalyzer.analyze(
                firstRemainingSlots,
                secondRemaining,
                execution.fastPathType() == ECORecipeClassifier.Type.DURABILITY_MUTATION);
            return !second.rejected() && second.model() != null && model.sameTransition(second.model());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private List<ItemStack> snapshotSlots() {
        List<ItemStack> result = new ArrayList<>(craftingInventory.getContainerSize());
        for (int slot = 0; slot < craftingInventory.getContainerSize(); slot++) {
            result.add(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copy(craftingInventory.getItem(slot)));
        }
        return result;
    }

    private List<ItemStack> snapshotInputs() {
        return snapshotSlots().stream()
            .filter(stack -> !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack))
            .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
    }

    private static String negative(ECOCraftingFastPathCache cache,
        cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathKey key, long tick, String reason) {
        cache.putNegative(key, tick, reason);
        return reason;
    }

    private static String formatSlots(List<ItemStack> slots) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < slots.size(); index++) {
            ItemStack stack = slots.get(index);
            result.append("\n  {index=")
                .append(index);
            if (stack == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack))
                result.append(", AEKey=null, amount=0, state=EMPTY}");
            else result.append(", AEKey=")
                .append(AEItemKey.of(stack))
                .append(", amount=")
                .append(cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(stack))
                .append(", components=")
                .append(stack.getTagCompound())
                .append(", damage=")
                .append(stack.getItemDamage())
                .append('}');
        }
        return result.append("\n]")
            .toString();
    }

    @com.github.bsideup.jabel.Desugar
    record PreparedWork(List<ItemStack> outputs, List<ItemStack> inputs, List<ItemStack> remaining, String reason) {

        PreparedWork {

        }
    }
}
