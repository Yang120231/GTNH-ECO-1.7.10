package cn.dancingsnow.neoecoae.crafting.planner.trace;

import java.util.List;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

@com.github.bsideup.jabel.Desugar
public record ComponentTrace(int componentId, Type type, List<AEKey> members) {

    public enum Type {
        ACYCLIC,
        CYCLIC
    }

    public ComponentTrace {
        members = members;
    }
}
