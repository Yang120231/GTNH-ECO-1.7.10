package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Precomputed special-input contract. Empty is the allocation-free normal-pattern fast path. */
@com.github.bsideup.jabel.Desugar
public record SpecialPatternAnalysis(List<Requirement> requirements) {

    public static final SpecialPatternAnalysis NONE = new SpecialPatternAnalysis(
        cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf());

    public SpecialPatternAnalysis {

    }

    public boolean special() {
        return !requirements.isEmpty();
    }

    public Requirement requirementFor(CompiledInput input) {
        for (Requirement requirement : requirements) {
            if (requirement.input() == input) return requirement;
        }
        return null;
    }

    public boolean excludesFromCycleGraph(CompiledInput input) {
        return requirementFor(input) != null;
    }

    public enum Type {
        DURABILITY,
        CONTAINER,
        REUSABLE,
        CATALYST
    }

    @com.github.bsideup.jabel.Desugar
    public record Requirement(CompiledInput input, AEKey returnedKey, Type type, int damagePerUse, int maxDamage) {}
}
