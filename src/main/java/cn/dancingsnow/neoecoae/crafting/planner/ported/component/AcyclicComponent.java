package cn.dancingsnow.neoecoae.crafting.planner.ported.component;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;

/** A singleton SCC with no self-loop. */
public final class AcyclicComponent implements PlanningComponent {

    private final int componentId;

    public int componentId() {
        return componentId;
    }

    private final Object key;

    public Object key() {
        return key;
    }

    private final List<CompiledPattern> patterns;

    public List<CompiledPattern> patterns() {
        return patterns;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof AcyclicComponent)) return false;
        AcyclicComponent other = (AcyclicComponent) value;
        return java.util.Objects.equals(componentId, other.componentId) && java.util.Objects.equals(key, other.key)
            && java.util.Objects.equals(patterns, other.patterns);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(componentId, key, patterns);
    }

    public AcyclicComponent(int componentId, Object key, List<CompiledPattern> patterns) {
        patterns = com.google.common.collect.ImmutableList.copyOf(patterns);

        this.componentId = componentId;
        this.key = key;
        this.patterns = patterns;
    }

    @Override
    public List<Object> members() {
        return com.google.common.collect.ImmutableList.of(key);
    }
}
