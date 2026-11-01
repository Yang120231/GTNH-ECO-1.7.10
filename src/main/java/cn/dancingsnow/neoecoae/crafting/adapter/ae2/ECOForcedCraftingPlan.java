package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import java.util.Map;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/**
 * Turns a simulation plan into a submit-ready plan (1.21's force start without ExtendedAE Plus): missing inputs join
 * the emitted items, so the CPU's persisted waiting inventory receives them like any other awaited material.
 *
 * <p>
 * A stock shortfall (inputs the plan counted as stored but the network no longer holds at submission) is moved the
 * same way: the CPU extracts only what is still there and waits for the rest.
 * </p>
 */
public final class ECOForcedCraftingPlan implements ICraftingPlan {

    private final ICraftingPlan delegate;
    private final KeyCounter usedItems;
    private final KeyCounter waitingItems = new KeyCounter();

    public ECOForcedCraftingPlan(ICraftingPlan delegate) {
        this(delegate, new KeyCounter());
    }

    public ECOForcedCraftingPlan(ICraftingPlan delegate, KeyCounter stockShortfall) {
        this.delegate = delegate;
        usedItems = delegate.usedItems()
            .copy();
        waitingItems.addAll(delegate.emittedItems());
        // Merely hiding missingItems would accept the job but leave it unable to receive its missing inputs.
        for (var entry : delegate.missingItems())
            waitingItems.set(entry.getKey(), Math.addExact(waitingItems.get(entry.getKey()), entry.getLongValue()));
        for (var entry : stockShortfall) {
            long moved = Math.min(entry.getLongValue(), usedItems.get(entry.getKey()));
            if (moved <= 0) continue;
            usedItems.set(entry.getKey(), usedItems.get(entry.getKey()) - moved);
            waitingItems.set(entry.getKey(), Math.addExact(waitingItems.get(entry.getKey()), moved));
        }
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
        return usedItems.copy();
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
