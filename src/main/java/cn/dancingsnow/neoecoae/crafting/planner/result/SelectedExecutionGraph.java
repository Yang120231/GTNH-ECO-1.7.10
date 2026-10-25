package cn.dancingsnow.neoecoae.crafting.planner.result;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Final physical dependency graph. It is built from selected task semantics, never structural candidates. */
@com.github.bsideup.jabel.Desugar
public record SelectedExecutionGraph(Set<IPatternDetails> tasks, List<ExecutionDependency> dependencies) {

    public SelectedExecutionGraph {

    }

    public static SelectedExecutionGraph build(Map<IPatternDetails, PatternSemantics> selected) {
        List<IPatternDetails> patterns = new ArrayList<>(selected.keySet());
        List<ExecutionDependency> edges = new ArrayList<>();
        for (int c = 0; c < patterns.size(); c++) {
            IPatternDetails consumer = patterns.get(c);
            PatternSemantics cs = selected.get(consumer);
            for (PatternSemantics.Input input : cs.consumedInputs()) {
                AEKey key = input.key();
                for (int p = 0; p < patterns.size(); p++) {
                    if (p == c) continue;
                    IPatternDetails producer = patterns.get(p);
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
