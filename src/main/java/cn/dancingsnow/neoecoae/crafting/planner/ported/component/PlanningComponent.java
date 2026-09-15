package cn.dancingsnow.neoecoae.crafting.planner.ported.component;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.CompiledPattern;

public interface PlanningComponent {

    int componentId();

    List<Object> members();

    List<CompiledPattern> patterns();

    default boolean cyclic() {
        return this instanceof CycleComponent;
    }
}
