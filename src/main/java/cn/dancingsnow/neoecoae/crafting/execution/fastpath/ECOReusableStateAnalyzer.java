package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/** Selects a state proof conservatively; unknown or mixed transitions remain on the slow path. */
public final class ECOReusableStateAnalyzer {

    private ECOReusableStateAnalyzer() {}

    @com.github.bsideup.jabel.Desugar
    public record Analysis(@Nullable ECOReusableStateModel model, @Nullable String rejectReason) {

        public boolean rejected() {
            return rejectReason != null;
        }
    }

    public static Analysis analyze(List<ItemStack> before, List<ItemStack> after) {
        return analyze(before, after, false);
    }

    public static Analysis analyze(List<ItemStack> before, List<ItemStack> after, boolean allowObservedBrokenTool) {
        if (before.size() != after.size()) return new Analysis(null, "STATE_SLOT_COUNT_MISMATCH");
        boolean hasDurability = false;
        boolean hasReusableState = false;
        for (int i = 0; i < before.size(); i++) {
            ItemStack initial = before.get(i);
            ItemStack result = after.get(i);
            if (initial == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(initial)) continue;
            if (initial.isItemStackDamageable()) {
                if (result != null && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(result)
                    && cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItem(initial, result)) {
                    if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks
                        .isSameItemSameComponents(initial, result)) {
                        hasReusableState = true;
                    } else {
                        hasDurability = true;
                    }
                } else if (allowObservedBrokenTool
                    && (result == null || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(result))) {
                        hasDurability = true;
                    }
                continue;
            }
            if (result != null && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(result)
                && cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItem(initial, result)) {
                hasReusableState = true;
            }
        }
        if (hasDurability && hasReusableState) {
            var durability = ECODurabilityBatchModel.analyze(before, after);
            List<ItemStack> stateBefore = before.stream()
                .map(stack -> stack != null && stack.isItemStackDamageable() ? null : stack)
                .collect(cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.toList());
            var state = ECOStateTransitionBatchModel.analyze(stateBefore, after);
            if (!durability.isPresent()) return new Analysis(null, "DURABILITY_TRANSITION_INVALID");
            // Unchanged damageable catalysts are already represented by a zero-delta durability transition.
            if (!state.isPresent()) {
                for (int i = 0; i < stateBefore.size(); i++) {
                    ItemStack initial = stateBefore.get(i);
                    ItemStack result = after.get(i);
                    if (initial != null && result != null
                        && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(initial)
                        && !cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(result)
                        && cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItem(initial, result)) {
                        return new Analysis(null, "STATE_TRANSITION_NOT_PROVABLY_LINEAR");
                    }
                }
                return new Analysis(durability.get(), null);
            }
            return new Analysis(new ECOCombinedStateModel(durability.get(), state.get()), null);
        }
        if (hasDurability) {
            return ECODurabilityBatchModel.analyze(before, after)
                .<Analysis>map(model -> new Analysis(model, null))
                .orElseGet(() -> new Analysis(null, "DURABILITY_TRANSITION_INVALID"));
        }
        if (hasReusableState) {
            return ECOStateTransitionBatchModel.analyze(before, after)
                .<Analysis>map(model -> new Analysis(model, null))
                .orElseGet(() -> new Analysis(null, "STATE_TRANSITION_NOT_PROVABLY_LINEAR"));
        }
        return new Analysis(null, null);
    }
}
