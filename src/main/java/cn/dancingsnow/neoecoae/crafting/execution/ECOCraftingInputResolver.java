package cn.dancingsnow.neoecoae.crafting.execution;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ICraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/** ECO dispatch equivalent of AE2 input extraction with reload-aware remainder memoization. */
final class ECOCraftingInputResolver {

    private ECOCraftingInputResolver() {}

    @Nullable
    static KeyCounter[] extractPatternInputs(IPatternDetails details, ICraftingInventory sourceInventory, World level,
        KeyCounter expectedOutputs, KeyCounter expectedContainerItems, ECOCraftingRemainderCache remainderCache) {
        return extractPatternInputs(
            details,
            sourceInventory,
            level,
            expectedOutputs,
            expectedContainerItems,
            remainderCache,
            true);
    }

    /** Resolve against a fresh overlay whose virtual removals are discarded after a failed attempt. */
    @Nullable
    static KeyCounter[] extractPatternInputsFromDisposablePreview(IPatternDetails details,
        ECOCraftingInputPreview sourceInventory, World level, KeyCounter expectedOutputs,
        KeyCounter expectedContainerItems, ECOCraftingRemainderCache remainderCache) {
        return extractPatternInputs(
            details,
            sourceInventory,
            level,
            expectedOutputs,
            expectedContainerItems,
            remainderCache,
            false);
    }

    @Nullable
    private static KeyCounter[] extractPatternInputs(IPatternDetails details, ICraftingInventory sourceInventory,
        World level, KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
        ECOCraftingRemainderCache remainderCache, boolean rollbackOnFailure) {
        var inputs = details.getInputs();
        KeyCounter[] inputHolder = new KeyCounter[inputs.length];
        boolean found = true;

        for (int slot = 0; slot < inputs.length; slot++) {
            var input = inputs[slot];
            var extractedInputs = inputHolder[slot] = new KeyCounter();
            long remainingMultiplier = input.getMultiplier();
            if (sourceInventory instanceof ECOCraftingInputPreview preview) {
                long primaryCrafts = preview.extractPrimaryInput(
                    input,
                    remainingMultiplier,
                    level,
                    extractedInputs,
                    expectedContainerItems,
                    remainderCache);
                if (primaryCrafts >= 0L) {
                    remainingMultiplier -= primaryCrafts;
                    if (remainingMultiplier > 0L) {
                        found = false;
                        break;
                    }
                    continue;
                }
            }
            var preview = sourceInventory instanceof ECOCraftingInputPreview overlay ? overlay : null;
            var templates = preview == null ? CraftingCpuHelper.getValidItemTemplates(sourceInventory, input, level)
                : preview.inputTemplates(input, level);
            for (var template : templates) {
                if (preview != null && preview.needsTemplateValidation() && !input.isValid(template.key(), level)) {
                    continue;
                }
                long extracted = preview == null
                    ? CraftingCpuHelper.extractTemplates(sourceInventory, template, remainingMultiplier)
                    : preview.extractTemplates(template, remainingMultiplier);
                if (extracted <= 0L) continue;
                extractedInputs.add(template.key(), extracted * template.amount());

                var remainder = remainderCache.get(input, template.key());
                if (remainder != null) {
                    expectedContainerItems.add(remainder, extracted);
                }

                remainingMultiplier -= extracted;
                if (remainingMultiplier == 0L) {
                    break;
                }
            }

            if (remainingMultiplier > 0L) {
                found = false;
                break;
            }
        }

        if (!found && rollbackOnFailure) {
            CraftingCpuHelper.reinjectPatternInputs(sourceInventory, inputHolder);
        }
        if (!found) {
            return null;
        }

        for (var output : details.getOutputs()) {
            expectedOutputs.add(output.what(), output.amount());
        }
        return inputHolder;
    }
}
