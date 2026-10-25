package cn.dancingsnow.neoecoae.crafting.planner.route;

import java.util.List;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

/** Goal-first topological key order for the reachable dependency graph. */
@com.github.bsideup.jabel.Desugar
public record AcyclicRoutePlan(List<AEKey> keys) {

    public AcyclicRoutePlan {
        keys = keys;
    }
}
