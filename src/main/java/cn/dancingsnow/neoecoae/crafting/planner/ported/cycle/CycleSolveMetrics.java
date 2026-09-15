package cn.dancingsnow.neoecoae.crafting.planner.ported.cycle;

/** Search accounting, reported for every outcome so budget verdicts stay auditable. */
public final class CycleSolveMetrics {

    private final int relevantKeys;

    public int relevantKeys() {
        return relevantKeys;
    }

    private final int transitions;

    public int transitions() {
        return transitions;
    }

    private final long statesVisited;

    public long statesVisited() {
        return statesVisited;
    }

    private final long statesExpanded;

    public long statesExpanded() {
        return statesExpanded;
    }

    private final /**
                   * Expanded per-firing witness length; zero means the exact witness is retained only in compact batch
                   * form.
                   */
    int witnessLength;

    public /**
            * Expanded per-firing witness length; zero means the exact witness is retained only in compact batch form.
            */
    int witnessLength() {
        return witnessLength;
    }

    private final int seedLadderSteps;

    public int seedLadderSteps() {
        return seedLadderSteps;
    }

    private final boolean stateBudgetExhausted;

    public boolean stateBudgetExhausted() {
        return stateBudgetExhausted;
    }

    private final boolean firingDepthTruncated;

    public boolean firingDepthTruncated() {
        return firingDepthTruncated;
    }

    private final boolean amountOverflowTruncated;

    public boolean amountOverflowTruncated() {
        return amountOverflowTruncated;
    }

    private final long greedyCandidates;

    public long greedyCandidates() {
        return greedyCandidates;
    }

    private final long lookaheadNodes;

    public long lookaheadNodes() {
        return lookaheadNodes;
    }

    private final long heuristicMacroSteps;

    public long heuristicMacroSteps() {
        return heuristicMacroSteps;
    }

    private final boolean heuristicBudgetExhausted;

    public boolean heuristicBudgetExhausted() {
        return heuristicBudgetExhausted;
    }

    public CycleSolveMetrics(int relevantKeys, int transitions, long statesVisited, long statesExpanded,
        /** Expanded per-firing witness length; zero means the exact witness is retained only in compact batch form. */
        int witnessLength, int seedLadderSteps, boolean stateBudgetExhausted, boolean firingDepthTruncated,
        boolean amountOverflowTruncated, long greedyCandidates, long lookaheadNodes, long heuristicMacroSteps,
        boolean heuristicBudgetExhausted) {
        this.relevantKeys = relevantKeys;
        this.transitions = transitions;
        this.statesVisited = statesVisited;
        this.statesExpanded = statesExpanded;
        this.witnessLength = witnessLength;
        this.seedLadderSteps = seedLadderSteps;
        this.stateBudgetExhausted = stateBudgetExhausted;
        this.firingDepthTruncated = firingDepthTruncated;
        this.amountOverflowTruncated = amountOverflowTruncated;
        this.greedyCandidates = greedyCandidates;
        this.lookaheadNodes = lookaheadNodes;
        this.heuristicMacroSteps = heuristicMacroSteps;
        this.heuristicBudgetExhausted = heuristicBudgetExhausted;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CycleSolveMetrics)) return false;
        CycleSolveMetrics other = (CycleSolveMetrics) value;
        return java.util.Objects.equals(relevantKeys, other.relevantKeys)
            && java.util.Objects.equals(transitions, other.transitions)
            && java.util.Objects.equals(statesVisited, other.statesVisited)
            && java.util.Objects.equals(statesExpanded, other.statesExpanded)
            && java.util.Objects.equals(witnessLength, other.witnessLength)
            && java.util.Objects.equals(seedLadderSteps, other.seedLadderSteps)
            && java.util.Objects.equals(stateBudgetExhausted, other.stateBudgetExhausted)
            && java.util.Objects.equals(firingDepthTruncated, other.firingDepthTruncated)
            && java.util.Objects.equals(amountOverflowTruncated, other.amountOverflowTruncated)
            && java.util.Objects.equals(greedyCandidates, other.greedyCandidates)
            && java.util.Objects.equals(lookaheadNodes, other.lookaheadNodes)
            && java.util.Objects.equals(heuristicMacroSteps, other.heuristicMacroSteps)
            && java.util.Objects.equals(heuristicBudgetExhausted, other.heuristicBudgetExhausted);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
            relevantKeys,
            transitions,
            statesVisited,
            statesExpanded,
            witnessLength,
            seedLadderSteps,
            stateBudgetExhausted,
            firingDepthTruncated,
            amountOverflowTruncated,
            greedyCandidates,
            lookaheadNodes,
            heuristicMacroSteps,
            heuristicBudgetExhausted);
    }

    public static final CycleSolveMetrics NONE = new CycleSolveMetrics(
        0,
        0,
        0,
        0,
        0,
        0,
        false,
        false,
        false,
        0,
        0,
        0,
        false);

    public CycleSolveMetrics(int relevantKeys, int transitions, long statesVisited, long statesExpanded,
        int witnessLength, int seedLadderSteps, boolean stateBudgetExhausted, boolean firingDepthTruncated,
        boolean amountOverflowTruncated) {
        this(
            relevantKeys,
            transitions,
            statesVisited,
            statesExpanded,
            witnessLength,
            seedLadderSteps,
            stateBudgetExhausted,
            firingDepthTruncated,
            amountOverflowTruncated,
            0,
            0,
            0,
            false);
    }

    public CycleSolveMetrics(int relevantKeys, int transitions, long statesVisited, long statesExpanded,
        int witnessLength, int seedLadderSteps, boolean stateBudgetExhausted, boolean firingDepthTruncated) {
        this(
            relevantKeys,
            transitions,
            statesVisited,
            statesExpanded,
            witnessLength,
            seedLadderSteps,
            stateBudgetExhausted,
            firingDepthTruncated,
            false,
            0,
            0,
            0,
            false);
    }

    /** True when the search stopped early, so no infeasibility claim may be derived from it. */
    public boolean truncated() {
        return stateBudgetExhausted || firingDepthTruncated || amountOverflowTruncated;
    }
}
