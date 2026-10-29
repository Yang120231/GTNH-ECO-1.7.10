package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/**
 * Server-thread memo across recipe view rebuilds of one world. Each (pattern, input, key) is matched against the
 * native recipe at most once, so a stock change only validates keys never seen before instead of the whole network.
 */
final class NativeSubstitutionIndex implements NativePatternDetails.Substitutes {

    private static final int MIN_RESET_SIZE = 4096;
    private final World world;
    /** Every key ever offered, in first-seen order; inputs catch up from their own cursor. */
    private final List<AEKey> universe = new ArrayList<>();
    private final Set<AEKey> known = new HashSet<>();
    private final Map<ICraftingPatternDetails, List<Input>> inputs = new IdentityHashMap<>();
    private final Map<ICraftingPatternDetails, NativePatternDetails> details = new IdentityHashMap<>();
    private Set<AEKey> available = Collections.emptySet();

    NativeSubstitutionIndex(World world) {
        this.world = world;
    }

    void begin(Set<AEKey> available) {
        if (universe.size() > Math.max(MIN_RESET_SIZE, available.size() * 4L)) {
            // Long-gone keys dominate; restart rather than keep validating stock that left the network.
            universe.clear();
            known.clear();
            inputs.clear();
        }
        for (AEKey key : available) if (known.add(key)) universe.add(key);
        this.available = available;
    }

    /** Patterns without substitution never depend on stock, so their frozen details are reused as-is. */
    NativePatternDetails details(ICraftingPatternDetails pattern) {
        NativePatternDetails previous = details.get(pattern);
        if (previous != null && !(pattern.isCraftable() && pattern.canSubstitute())) return previous;
        NativePatternDetails captured = NativePatternDetails.capture(pattern, world, this);
        details.put(pattern, captured);
        return captured;
    }

    void retain(Set<ICraftingPatternDetails> live) {
        inputs.keySet()
            .retainAll(live);
        details.keySet()
            .retainAll(live);
    }

    @Override
    public Iterable<AEKey> accepted(ICraftingPatternDetails pattern, int index, AEKey primary, GenericStack[] sparse) {
        List<Input> slots = inputs.computeIfAbsent(pattern, ignored -> new ArrayList<>());
        while (slots.size() <= index) slots.add(null);
        Input input = slots.get(index);
        if (input == null || !input.primary.equals(primary)) slots.set(index, input = new Input(primary));
        for (int size = universe.size(); input.tested < size; input.tested++) {
            AEKey candidate = universe.get(input.tested);
            if (NativePatternDetails.acceptsSubstitute(pattern, world, sparse, primary, candidate))
                input.valid.add(candidate);
        }
        List<AEKey> result = new ArrayList<>();
        for (AEKey candidate : input.valid) if (available.contains(candidate)) result.add(candidate);
        return result;
    }

    private static final class Input {

        final AEKey primary;
        final List<AEKey> valid = new ArrayList<>();
        int tested;

        Input(AEKey primary) {
            this.primary = primary;
        }
    }
}
