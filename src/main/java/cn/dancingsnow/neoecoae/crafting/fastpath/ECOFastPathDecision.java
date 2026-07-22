package cn.dancingsnow.neoecoae.crafting.fastpath;

public enum ECOFastPathDecision {

    ACCEPTED,
    DISABLED,
    NOT_ECO_CRAFTING_HOST,
    UNSAFE_PATTERN,
    NON_UNIQUE_PROCESSING_SOURCE,
    CACHE_NEGATIVE,
    ERROR;

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
