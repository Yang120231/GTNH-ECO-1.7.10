package cn.dancingsnow.neoecoae.crafting.runtime;

public enum ECOBatchCraftingState {

    PENDING,
    RUNNING,
    COMPLETE,
    FAILED,
    FALLBACK;

    public static ECOBatchCraftingState byName(String name) {
        if (name != null) {
            for (ECOBatchCraftingState state : values()) {
                if (state.name()
                    .equals(name)) {
                    return state;
                }
            }
        }
        return FALLBACK;
    }
}
