package cn.dancingsnow.neoecoae.crafting.planner.component;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** A singleton SCC with no self-loop. */
@com.github.bsideup.jabel.Desugar
public record AcyclicComponent(int componentId, AEKey key, List<CompiledPattern> patterns)
    implements PlanningComponent {

    public AcyclicComponent {

    }

    @Override
    public List<AEKey> members() {
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(key);
    }
}
