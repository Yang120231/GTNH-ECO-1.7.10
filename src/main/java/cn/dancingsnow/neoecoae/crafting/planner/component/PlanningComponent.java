package cn.dancingsnow.neoecoae.crafting.planner.component;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

public interface PlanningComponent {

    int componentId();

    List<AEKey> members();

    List<CompiledPattern> patterns();

    default boolean cyclic() {
        return this instanceof CycleComponent;
    }
}
