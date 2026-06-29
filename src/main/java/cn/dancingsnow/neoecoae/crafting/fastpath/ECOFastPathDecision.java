package cn.dancingsnow.neoecoae.crafting.fastpath;

public enum ECOFastPathDecision {

    ACCEPTED,
    DISABLED,
    NOT_ECO_CRAFTING_HOST,
    UNSAFE_PATTERN,
    CACHE_NEGATIVE,
    ERROR;

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
