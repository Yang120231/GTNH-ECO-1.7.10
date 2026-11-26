package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingPatternDispatcher.ProbeState;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/** Transient provider traversal only; contains no job progress or material state. */
final class ECOProviderCursor {

    private final Map<IPatternDetails, Cursor> cursors = new Reference2ObjectOpenHashMap<>();
    private ICraftingService service;
    private long revision;
    private long tick;

    void beginPass(ICraftingService currentService, long tick) {
        this.tick = tick;
        long currentRevision = currentService instanceof ECOCraftingProviderRevision source
            ? source.neoecoae$getProviderRevision()
            : 0;
        if (service != currentService) {
            cursors.clear();
            service = currentService;
        }
        revision = currentRevision;
    }

    /**
     * Checks each provider on demand, starting at the transient round-robin cursor.
     *
     * <p>
     * The caller can try the providers in this order without resolving the pattern inputs again. Scanning
     * a full snapshot preserves the cursor; the caller must call {@link #advanceAfter} only when actually
     * attempting a provider. A time-sliced scan resumes on its first uninspected peer.
     * </p>
     */
    Traversal availableProviders(IPatternDetails pattern, Supplier<Iterable<ICraftingProvider>> providers,
        ECOCraftingDispatchBudget budget, BiPredicate<ICraftingProvider, ProbeState> eligible,
        BiPredicate<ICraftingProvider, Boolean> availability) {
        Cursor previous = cursors.get(pattern);
        Cursor cursor = previous;
        if (cursor == null || cursor.revision != revision
            || (!(service instanceof ECOCraftingProviderRevision) && cursor.tick != tick)) {
            var snapshot = new ArrayList<ICraftingProvider>();
            providers.get()
                .forEach(snapshot::add);
            if (previous != null && sameBindings(previous.providers, snapshot)) {
                previous.revision = revision;
                previous.tick = tick;
            } else {
                cursor = new Cursor(snapshot, tick, revision, previous);
                cursors.put(pattern, cursor);
            }
        }
        return new Traversal(cursor, budget, eligible, availability);
    }

    private static boolean sameBindings(List<ICraftingProvider> a, List<ICraftingProvider> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private static Object identity(ICraftingProvider provider) {
        return provider instanceof cn.dancingsnow.neoecoae.platform.ae2.crafting.NativeCraftingProvider n
            ? n.nativeMedium()
            : provider;
    }

    static final class Traversal implements Iterator<ICraftingProvider> {

        private final List<ICraftingProvider> snapshot;
        private final int start;
        private final Cursor cursor;
        private final ECOCraftingDispatchBudget budget;
        private final BiPredicate<ICraftingProvider, ProbeState> eligible;
        private final BiPredicate<ICraftingProvider, Boolean> availability;
        private int checked;
        private ICraftingProvider next;
        private int shareChecked;
        private IntArrayFIFOQueue scaledAhead;

        private Traversal(Cursor cursor, ECOCraftingDispatchBudget budget,
            BiPredicate<ICraftingProvider, ProbeState> eligible, BiPredicate<ICraftingProvider, Boolean> availability) {
            this.cursor = cursor;
            this.budget = budget;
            this.snapshot = cursor.providers;
            this.start = cursor.next;
            this.eligible = eligible;
            this.availability = availability;
        }

        private ICraftingProvider at(int offset) {
            return snapshot.get((int) (((long) start + offset) % snapshot.size()));
        }

        ProbeState feedback(ICraftingProvider provider) {
            int index = cursor.indices.getInt(provider);
            var state = cursor.feedback[index];
            if (state == null) cursor.feedback[index] = state = new ProbeState();
            return state;
        }

        private boolean available(ICraftingProvider provider) {
            return eligible.test(provider, feedback(provider)) && availability.test(provider, provider.isBusy());
        }

        boolean hasUninspectedProviders() {
            return checked < snapshot.size();
        }

        /** Count peers after the current provider without consuming the dispatch iterator or moving its cursor. */
        long sharingProviders(long maxReceivers, Predicate<ICraftingProvider> participates) {
            if (maxReceivers <= 1 || !hasUninspectedProviders()) return 1;
            if (scaledAhead == null) scaledAhead = new IntArrayFIFOQueue();
            while (!scaledAhead.isEmpty() && scaledAhead.firstInt() < checked) scaledAhead.dequeueInt();
            shareChecked = Math.max(shareChecked, checked);
            // Stop as soon as further targets cannot change the current share.
            while (shareChecked < snapshot.size() && scaledAhead.size() < maxReceivers - 1 && budget.hasTime()) {
                int offset = shareChecked++;
                var peer = at(offset);
                if (participates.test(peer) && available(peer)) scaledAhead.enqueue(offset);
            }
            return Math.min(maxReceivers, 1L + scaledAhead.size());
        }

        @Override
        public boolean hasNext() {
            while (next == null && hasUninspectedProviders()) {
                // Yield between inspections, not in the middle of a selected provider transaction. Resume
                // on the first uninspected peer even if all earlier ones were busy and none was attempted.
                if (checked > 0 && !budget.hasTime()) {
                    cursor.next = cursor.indices.getInt(at(checked));
                    return false;
                }
                var provider = at(checked++);
                if (available(provider)) next = provider;
            }
            return next != null;
        }

        @Override
        public ICraftingProvider next() {
            if (!hasNext()) throw new NoSuchElementException();
            var provider = next;
            next = null;
            return provider;
        }
    }

    /** Records the provider currently being attempted so the next pass starts after it. */
    void advanceAfter(IPatternDetails pattern, ICraftingProvider provider) {
        Cursor cursor = cursors.get(pattern);
        if (cursor == null || cursor.providers.isEmpty()) {
            return;
        }
        int index = cursor.indices.getInt(provider);
        if (index >= 0) {
            cursor.next = (index + 1) % cursor.providers.size();
        }
    }

    void forget(IPatternDetails pattern) {
        cursors.remove(pattern);
    }

    void clear() {
        cursors.clear();
        service = null;
    }

    private static final class Cursor {

        final List<ICraftingProvider> providers;
        final Reference2IntOpenHashMap<ICraftingProvider> indices;
        final ProbeState[] feedback;
        long tick, revision;
        int next;

        Cursor(List<ICraftingProvider> providers, long tick, long revision, Cursor previous) {
            this.providers = providers;
            this.indices = new Reference2IntOpenHashMap<>(providers.size());
            this.indices.defaultReturnValue(-1);
            this.feedback = new ProbeState[providers.size()];
            var oldIndices = new Reference2IntOpenHashMap<Object>();
            oldIndices.defaultReturnValue(-1);
            if (previous != null) for (int i = 0; i < previous.providers.size(); i++)
                oldIndices.put(identity(previous.providers.get(i)), i);
            for (int index = 0; index < providers.size(); index++) {
                var provider = providers.get(index);
                this.indices.put(provider, index);
                int old = oldIndices.getInt(identity(provider));
                if (old >= 0 && old == previous.next) next = index;
                if (old >= 0) feedback[index] = previous.feedback[old];
            }
            this.tick = tick;
            this.revision = revision;
        }
    }
}
