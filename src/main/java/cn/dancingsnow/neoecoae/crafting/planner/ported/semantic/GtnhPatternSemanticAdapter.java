package cn.dancingsnow.neoecoae.crafting.planner.ported.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.solve.PlannerAmount;

/** Public GTNH pattern contract projected into the upstream semantic model. */
public final class GtnhPatternSemanticAdapter {

    public PatternSemantics analyze(ICraftingPatternDetails pattern) {
        Object definition = null;
        try {
            definition = pattern.getPattern();
            if (pattern.canSubstitute() || pattern.isInputOnly())
                return PatternSemantics.unsupported(pattern, definition, "UNPROVEN_GTNH_MATCHING_CONTRACT");
            List<PatternSemantics.Input> inputs = new ArrayList<>();
            List<GenericStack> outputs = new ArrayList<>();
            for (IAEStack<?> input : pattern.getCondensedAEInputs()) {
                if (input == null || input.getStackSize() <= 0)
                    return PatternSemantics.unsupported(pattern, definition, "INVALID_INPUT");
                if (pattern.isCraftable() && input instanceof IAEItemStack) {
                    var item = ((IAEItemStack) input).getItemStack();
                    if (item.getItem()
                        .hasContainerItem(item))
                        return PatternSemantics.unsupported(pattern, definition, "UNPROVEN_GTNH_REMAINDER_CONTRACT");
                }
                inputs.add(
                    new PatternSemantics.Input(
                        input,
                        new ECOResourceKey(input),
                        PlannerAmount.of(input.getStackSize()),
                        null,
                        PlannerAmount.ZERO));
            }
            for (IAEStack<?> output : pattern.getCondensedAEOutputs()) {
                if (output == null || output.getStackSize() <= 0)
                    return PatternSemantics.unsupported(pattern, definition, "INVALID_OUTPUT");
                outputs.add(new GenericStack(new ECOResourceKey(output), output.getStackSize()));
            }
            if (outputs.isEmpty()) return PatternSemantics.unsupported(pattern, definition, "MISSING_OUTPUT");
            return new PatternSemantics(
                pattern,
                definition,
                inputs,
                outputs,
                Collections.emptyList(),
                Collections.emptyList(),
                PatternSemantics.MatchingMode.EXACT,
                PatternSemantics.ExecutionRestriction.NONE,
                true,
                false,
                null);
        } catch (RuntimeException rejected) {
            return PatternSemantics.unsupported(
                pattern,
                definition,
                "MALFORMED_PATTERN:" + rejected.getClass()
                    .getSimpleName());
        }
    }
}
