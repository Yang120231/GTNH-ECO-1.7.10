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
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingService;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

/** Transient provider traversal only; contains no job progress or material state. */
final class ECOProviderCursor {

    private final Map<IPatternDetails, Cursor> cursors = new Object2ObjectOpenHashMap<>();
    private ICraftingService service;
    private long revision;
    private long tick;

    void beginPass(ICraftingService currentService, long tick) {
        this.tick = tick;
        long currentRevision = currentService instanceof ECOCraftingProviderRevision source
            ? source.neoecoae$getProviderRevision()
            : 0;
        if (service != currentService || revision != currentRevision) {
            cursors.clear();
            service = currentService;
            revision = currentRevision;
        }
    }

    /**
     * Checks each provider on demand, starting at the transient round-robin cursor.
     *
     * <p>
     * The caller can try the providers in this order without resolving the pattern inputs again. Scanning
     * a full snapshot preserves the cursor; the caller must call {@link #advanceAfter} only when actually
     * attempting a provider, so skipped providers cannot undo rejection fairness.
     * </p>
     */
    Iterator<ICraftingProvider> availableProviders(IPatternDetails pattern,
        Supplier<Iterable<ICraftingProvider>> providers, Predicate<ICraftingProvider> eligible,
        BiPredicate<ICraftingProvider, Boolean> availability) {
        Cursor previous = cursors.get(pattern);
        Cursor cursor = previous;
        if (cursor == null || (!(service instanceof ECOCraftingProviderRevision) && cursor.tick != tick)) {
            var snapshot = new ArrayList<ICraftingProvider>();
            providers.get()
                .forEach(snapshot::add);
            cursor = new Cursor(snapshot, tick);
            // Refresh fallback snapshots each tick while preserving the next live provider by identity.
            if (previous != null && !previous.providers.isEmpty()) {
                var nextProvider = previous.providers.get(previous.next);
                int nextIndex = cursor.indices.getInt(nextProvider);
                if (nextIndex >= 0) cursor.next = nextIndex;
            }
            cursors.put(pattern, cursor);
        }
        var snapshot = cursor.providers;
        int start = cursor.next;
        return new Iterator<>() {

            private int checked;
            private ICraftingProvider next;

            @Override
            public boolean hasNext() {
                while (next == null && checked < snapshot.size()) {
                    var provider = snapshot.get((start + checked++) % snapshot.size());
                    if (!eligible.test(provider)) continue;
                    boolean busy = provider.isBusy();
                    if (availability.test(provider, busy)) next = provider;
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
        };
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
        final long tick;
        int next;

        Cursor(List<ICraftingProvider> providers, long tick) {
            this.providers = providers;
            this.indices = new Reference2IntOpenHashMap<>(providers.size());
            this.indices.defaultReturnValue(-1);
            for (int index = 0; index < providers.size(); index++) {
                this.indices.put(providers.get(index), index);
            }
            this.tick = tick;
        }
    }
}
