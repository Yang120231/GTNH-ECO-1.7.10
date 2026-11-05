package cn.dancingsnow.neoecoae.platform.ui.crafting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** First-observed task order, scoped to one selected CPU and job. */
public final class CraftingStatusTaskOrder<K> {

    private final LinkedHashSet<K> order = new LinkedHashSet<>();
    private int serial = -1;
    private String job = "";

    public List<K> update(int serial, String job, List<K> active) {
        if (this.serial != serial || !Objects.equals(this.job, job)) {
            order.clear();
            this.serial = serial;
            this.job = job;
        }
        order.retainAll(new java.util.HashSet<>(active));
        order.addAll(active);
        return new ArrayList<>(order);
    }

    public void clear() {
        order.clear();
    }
}
