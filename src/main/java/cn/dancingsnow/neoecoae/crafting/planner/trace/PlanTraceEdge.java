package cn.dancingsnow.neoecoae.crafting.planner.trace;

import java.math.BigInteger;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

@com.github.bsideup.jabel.Desugar
public record PlanTraceEdge(AEKey from, AEKey to, long amount, BigInteger exactAmount) {

    public PlanTraceEdge(AEKey from, AEKey to, long amount) {
        this(from, to, amount, BigInteger.valueOf(amount));
    }
}
