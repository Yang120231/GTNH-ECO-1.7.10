package cn.dancingsnow.neoecoae.crafting.fastpath;

public final class ECOFastPathPlan {

    private final ECOFastPathDecision decision;
    private final ECOFastPathPatternProfile patternProfile;
    private final String reason;

    private ECOFastPathPlan(ECOFastPathDecision decision, ECOFastPathPatternProfile patternProfile, String reason) {
        this.decision = decision == null ? ECOFastPathDecision.ERROR : decision;
        this.patternProfile = patternProfile;
        this.reason = reason == null ? "" : reason;
    }

    public static ECOFastPathPlan accepted(ECOFastPathPatternProfile patternProfile) {
        return new ECOFastPathPlan(ECOFastPathDecision.ACCEPTED, patternProfile, "");
    }

    public static ECOFastPathPlan rejected(ECOFastPathDecision decision, String reason) {
        return new ECOFastPathPlan(decision, null, reason);
    }

    public ECOFastPathDecision getDecision() {
        return this.decision;
    }

    public ECOFastPathPatternProfile getPatternProfile() {
        return this.patternProfile;
    }

    public String getReason() {
        return this.reason;
    }

    public boolean accepted() {
        return this.decision.accepted();
    }
}
