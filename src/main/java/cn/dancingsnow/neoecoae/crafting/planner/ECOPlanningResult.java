package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable handoff: only SUCCESS results carry a materially validated execution schedule. */
public final class ECOPlanningResult<K, P> {

    public enum Status {
        SUCCESS,
        MISSING_ITEMS,
        CYCLE_UNRESOLVED,
        AMOUNT_OVERFLOW,
        CANCELLED,
        LIMIT_EXCEEDED
    }

    public final Status status;
    public final Map<K, Long> extracted;
    public final Map<K, Long> missing;
    public final List<Step<K, P>> schedule;
    public final long bytes;

    ECOPlanningResult(Status status, Map<K, Long> extracted, Map<K, Long> missing, List<Step<K, P>> schedule,
        long bytes) {
        this.status = status;
        this.extracted = Collections.unmodifiableMap(new LinkedHashMap<>(extracted));
        this.missing = Collections.unmodifiableMap(new LinkedHashMap<>(missing));
        this.schedule = Collections.unmodifiableList(new ArrayList<>(schedule));
        this.bytes = bytes;
    }

    public static final class Step<K, P> {

        public final ECORecipe<K, P> recipe;
        public final long crafts;

        public Step(ECORecipe<K, P> recipe, long crafts) {
            if (crafts <= 0) throw new IllegalArgumentException("Invalid craft count");
            this.recipe = recipe;
            this.crafts = crafts;
        }
    }
}
