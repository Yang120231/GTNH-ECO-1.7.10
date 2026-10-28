package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import java.util.Map;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/**
 * Turns a simulation plan into a submit-ready plan (1.21's force start without ExtendedAE Plus): missing inputs join
 * the emitted items, so the CPU's persisted waiting inventory receives them like any other awaited material.
 */
public final class ECOForcedCraftingPlan implements ICraftingPlan {

    private final ICraftingPlan delegate;
    private final KeyCounter waitingItems = new KeyCounter();

    public ECOForcedCraftingPlan(ICraftingPlan delegate) {
        this.delegate = delegate;
        waitingItems.addAll(delegate.emittedItems());
        // Merely hiding missingItems would accept the job but leave it unable to receive its missing inputs.
        for (var entry : delegate.missingItems())
            waitingItems.set(entry.getKey(), Math.addExact(waitingItems.get(entry.getKey()), entry.getLongValue()));
    }

    public ICraftingPlan delegate() {
        return delegate;
    }

    @Override
    public GenericStack finalOutput() {
        return delegate.finalOutput();
    }

    @Override
    public long bytes() {
        return delegate.bytes();
    }

    @Override
    public boolean simulation() {
        return false;
    }

    @Override
    public boolean multiplePaths() {
        return delegate.multiplePaths();
    }

    @Override
    public KeyCounter usedItems() {
        return delegate.usedItems();
    }

    @Override
    public KeyCounter emittedItems() {
        return waitingItems.copy();
    }

    @Override
    public KeyCounter missingItems() {
        return new KeyCounter();
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return delegate.patternTimes();
    }
}
