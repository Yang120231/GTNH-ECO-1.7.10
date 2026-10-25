package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;

/** Recognizes remainder/catalyst state transitions before structural graph construction. */
public final class SpecialPatternAnalyzer {

    private final Function<CompiledInput, SpecialPatternAnalysis.Requirement> inspector;

    public SpecialPatternAnalyzer() {
        this(SpecialPatternAnalyzer::classify);
    }

    /** Injectable state inspector for integrations and registry-free planner tests. */
    public SpecialPatternAnalyzer(Function<CompiledInput, SpecialPatternAnalysis.Requirement> inspector) {
        this.inspector = Objects.requireNonNull(inspector);
    }

    public SpecialPatternAnalysis analyze(int patternId, IPatternDetails pattern, PatternSemantics semantics,
        List<CompiledInput> inputs) {
        // The compiled slot is the remainder contract, including the raw-input fallback. Optional aggregate
        // returnedOutputs must not gate catalyst/durability recognition or reintroduce these inputs into cycles.
        List<SpecialPatternAnalysis.Requirement> requirements = new ArrayList<>();
        for (CompiledInput input : inputs) {
            if (input.remainderKey() == null || input.remainderAmountPerPattern()
                .signum() <= 0) continue;
            SpecialPatternAnalysis.Requirement requirement = inspector.apply(input);
            if (requirement != null) requirements.add(requirement);
        }
        if (requirements.isEmpty()) return SpecialPatternAnalysis.NONE;

        return new SpecialPatternAnalysis(requirements);
    }

    private static SpecialPatternAnalysis.Requirement classify(CompiledInput input) {
        if (!(input.key() instanceof AEItemKey sourceKey) || !(input.remainderKey() instanceof AEItemKey returnedKey))
            return null;
        ItemStack source = sourceKey.toStack(1);
        ItemStack returned = returnedKey.toStack(1);
        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(source)
            || cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(returned)) return null;

        SpecialPatternAnalysis.Type type;
        int damagePerUse = 0;
        int maxDamage = 0;
        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItem(source, returned)) {
            if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isSameItemSameComponents(source, returned)) {
                type = SpecialPatternAnalysis.Type.REUSABLE;
            } else if (source.isItemStackDamageable() && returned.isItemStackDamageable()) {
                damagePerUse = returned.getItemDamage() - source.getItemDamage();
                if (damagePerUse <= 0) return null;
                maxDamage = source.getMaxDamage();
                type = SpecialPatternAnalysis.Type.DURABILITY;
            } else {
                type = SpecialPatternAnalysis.Type.CATALYST;
            }
        } else {
            type = SpecialPatternAnalysis.Type.CONTAINER;
        }
        return new SpecialPatternAnalysis.Requirement(input, input.remainderKey(), type, damagePerUse, maxDamage);
    }
}
