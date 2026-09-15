package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ported.semantic.PatternSemantics;

/** Final physical dependency graph. It is built from selected task semantics, never structural candidates. */
public final class SelectedExecutionGraph {

    private final Set<ICraftingPatternDetails> tasks;

    public Set<ICraftingPatternDetails> tasks() {
        return tasks;
    }

    private final List<ExecutionDependency> dependencies;

    public List<ExecutionDependency> dependencies() {
        return dependencies;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof SelectedExecutionGraph)) return false;
        SelectedExecutionGraph other = (SelectedExecutionGraph) value;
        return java.util.Objects.equals(tasks, other.tasks)
            && java.util.Objects.equals(dependencies, other.dependencies);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(tasks, dependencies);
    }

    public SelectedExecutionGraph(Set<ICraftingPatternDetails> tasks, List<ExecutionDependency> dependencies) {
        tasks = com.google.common.collect.ImmutableSet.copyOf(tasks);
        dependencies = com.google.common.collect.ImmutableList.copyOf(dependencies);

        this.tasks = tasks;
        this.dependencies = dependencies;
    }

    public static SelectedExecutionGraph build(Map<ICraftingPatternDetails, PatternSemantics> selected) {
        List<ICraftingPatternDetails> patterns = new ArrayList<>(selected.keySet());
        List<ExecutionDependency> edges = new ArrayList<>();
        for (int c = 0; c < patterns.size(); c++) {
            ICraftingPatternDetails consumer = patterns.get(c);
            PatternSemantics cs = selected.get(consumer);
            for (PatternSemantics.Input input : cs.consumedInputs()) {
                Object key = input.key();
                for (int p = 0; p < patterns.size(); p++) {
                    if (p == c) continue;
                    ICraftingPatternDetails producer = patterns.get(p);
                    PatternSemantics ps = selected.get(producer);
                    if (ps.producedKeys()
                        .contains(key)
                        || ps.returnedKeys()
                            .contains(key)) {
                        edges.add(new ExecutionDependency(producer, consumer, key));
                    }
                }
            }
        }
        return new SelectedExecutionGraph(new LinkedHashSet<>(patterns), edges);
    }
}
