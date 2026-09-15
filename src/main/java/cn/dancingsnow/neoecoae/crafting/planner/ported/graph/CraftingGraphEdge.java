package cn.dancingsnow.neoecoae.crafting.planner.ported.graph;

import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;

/** A candidate pattern's producer -> logical required-input relationship. */
public final class CraftingGraphEdge {

    private final Object producer;

    public Object producer() {
        return producer;
    }

    private final Object requiredInput;

    public Object requiredInput() {
        return requiredInput;
    }

    private final CompiledPattern pattern;

    public CompiledPattern pattern() {
        return pattern;
    }

    private final CompiledInput input;

    public CompiledInput input() {
        return input;
    }

    public CraftingGraphEdge(Object producer, Object requiredInput, CompiledPattern pattern, CompiledInput input) {
        this.producer = producer;
        this.requiredInput = requiredInput;
        this.pattern = pattern;
        this.input = input;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CraftingGraphEdge)) return false;
        CraftingGraphEdge other = (CraftingGraphEdge) value;
        return java.util.Objects.equals(producer, other.producer)
            && java.util.Objects.equals(requiredInput, other.requiredInput)
            && java.util.Objects.equals(pattern, other.pattern)
            && java.util.Objects.equals(input, other.input);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(producer, requiredInput, pattern, input);
    }

}
