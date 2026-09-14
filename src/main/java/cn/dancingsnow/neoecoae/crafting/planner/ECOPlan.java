package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.Collections;
import java.util.Map;

public final class ECOPlan {

    private final boolean success;
    private final Map<ECOItemKey, Long> crafts;
    private final Map<ECOItemKey, Long> missing;

    ECOPlan(boolean success, Map<ECOItemKey, Long> crafts, Map<ECOItemKey, Long> missing) {
        this.success = success;
        this.crafts = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(crafts));
        this.missing = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(missing));
    }

    public boolean success() {
        return success;
    }

    public Map<ECOItemKey, Long> crafts() {
        return crafts;
    }

    public Map<ECOItemKey, Long> missing() {
        return missing;
    }
}
