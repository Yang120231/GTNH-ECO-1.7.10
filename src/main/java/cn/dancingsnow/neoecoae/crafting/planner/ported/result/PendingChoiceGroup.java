package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import java.util.List;
import java.util.Map;

/** Explicit mutually-exclusive dynamic reservation choices. */
public final class PendingChoiceGroup {

    private final String id;

    public String id() {
        return id;
    }

    private final List<Map<?, Long>> branches;

    public List<Map<?, Long>> branches() {
        return branches;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof PendingChoiceGroup)) return false;
        PendingChoiceGroup other = (PendingChoiceGroup) value;
        return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(branches, other.branches);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, branches);
    }

    public PendingChoiceGroup(String id, List<Map<?, Long>> branches) {
        if (id == null || branches == null || branches.isEmpty())
            throw new IllegalArgumentException("Invalid choice group");
        List<Map<?, Long>> copy = new java.util.ArrayList<>(branches.size());
        for (Map<?, Long> branch : branches) copy.add(com.google.common.collect.ImmutableMap.copyOf(branch));
        branches = com.google.common.collect.ImmutableList.copyOf(copy);

        this.id = id;
        this.branches = branches;
    }
}
