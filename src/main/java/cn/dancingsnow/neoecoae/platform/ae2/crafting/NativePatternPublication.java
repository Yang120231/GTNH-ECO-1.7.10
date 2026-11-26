package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.PatternHelper;
import appeng.helpers.UltimatePatternHelper;

/** One server-owned publication journal per native grid. Never run device callbacks on a planning thread. */
public final class NativePatternPublication {

    public interface Owner {

        void neoecoae$flushPatterns();
    }

    private static final Set<Owner> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ICraftingProvider, Contribution> published = new IdentityHashMap<>();
    private final Set<ICraftingProvider> dirty = Collections.newSetFromMap(new IdentityHashMap<>());
    private Contribution recording;
    private boolean full, initialized, busy, probing;

    public static void flushTick() {
        if (!PENDING.isEmpty()) for (Owner owner : new ArrayList<>(PENDING)) owner.neoecoae$flushPatterns();
    }

    public static void stopped() {
        PENDING.clear();
    }

    public void request(Owner owner, ICraftingProvider provider) {
        if (provider == null) full = true;
        else dirty.add(provider);
        PENDING.add(owner);
    }

    public boolean pending() {
        return full || !dirty.isEmpty();
    }

    public boolean busy() {
        return busy;
    }

    private void take(Owner owner) {
        PENDING.remove(owner);
        dirty.clear();
        full = false;
    }

    public void fullRebuild(Owner owner, Runnable rebuild) {
        take(owner);
        published.clear();
        initialized = false;
        busy = true;
        try {
            rebuild.run();
            initialized = true;
        } finally {
            recording = null;
            busy = false;
        }
    }

    /** Keep the real AE2 helper as the receiver, even for providers inspecting its concrete type. */
    public void record(ICraftingProvider provider, Runnable call) {
        Contribution next = new Contribution();
        recording = next;
        try {
            call.run();
            published.put(provider, next);
        } finally {
            recording = null;
        }
    }

    public boolean option(ICraftingMedium medium, ICraftingPatternDetails pattern) {
        if (recording != null) recording.options.add(new Option(medium, pattern));
        return recording != null && probing;
    }

    public boolean emitter(ICraftingMedium medium, IAEStack<?> key) {
        if (recording != null) recording.emitters.add(new Emitter(medium, key));
        return recording != null && probing;
    }

    public boolean needsFull(Set<ICraftingProvider> providers) {
        return full || !initialized
            || published.size() != providers.size()
            || !published.keySet()
                .containsAll(dirty);
    }

    /** Only dirty providers execute. Replay affected native keys in native provider order; equal keys may be shared. */
    public void flush(Owner owner, Set<ICraftingProvider> providers, ICraftingProviderHelper helper,
        Map<ICraftingPatternDetails, List<ICraftingMedium>> methods, Map<IAEStack<?>, List<ICraftingMedium>> emissions,
        java.util.function.Supplier<Runnable> prepareIndexes) {
        List<ICraftingProvider> batch = new ArrayList<>(dirty);
        take(owner);
        Set<ICraftingPatternDetails> patterns = new HashSet<>();
        Set<IAEStack<?>> keys = new HashSet<>();
        busy = true;
        try {
            probing = true;
            for (ICraftingProvider provider : batch) {
                Contribution old = published.get(provider);
                Contribution next = new Contribution();
                recording = next;
                provider.provideCrafting(helper);
                recording = null;
                if (full) return; // Reentrant topology change queued a native rebuild. Do not publish a partial view.
                if (next.collectChanges(old, patterns, keys)) published.put(provider, next);
            }
            probing = false;
            if (patterns.isEmpty() && keys.isEmpty()) return;
            Runnable publishIndexes = prepareIndexes.get();
            for (ICraftingPatternDetails pattern : patterns) methods.remove(pattern);
            for (IAEStack<?> key : keys) emissions.remove(key);
            for (ICraftingProvider provider : providers) {
                Contribution source = published.get(provider);
                for (Option option : source.options)
                    if (patterns.contains(option.pattern)) helper.addCraftingOption(option.medium, option.pattern);
                for (Emitter emitter : source.emitters)
                    if (keys.contains(emitter.key)) helper.setEmitable(emitter.medium, emitter.key);
            }
            publishIndexes.run();
        } catch (RuntimeException | Error failed) {
            request(owner, null);
            throw failed;
        } finally {
            recording = null;
            probing = false;
            busy = false;
        }
    }

    private static final class Contribution {

        final List<Option> options = new ArrayList<>();
        final List<Emitter> emitters = new ArrayList<>();

        boolean collectChanges(Contribution old, Set<ICraftingPatternDetails> patterns, Set<IAEStack<?>> keys) {
            boolean changed = false;
            for (int i = 0; i < Math.max(options.size(), old.options.size()); i++) {
                Option a = i < options.size() ? options.get(i) : null;
                Option b = i < old.options.size() ? old.options.get(i) : null;
                if (a != null && b != null && a.same(b)) {
                    options.set(i, b); // Keep the very instance that is installed in AE's maps.
                    continue;
                }
                if (a != null) patterns.add(a.pattern);
                if (b != null) patterns.add(b.pattern);
                changed = true;
            }
            for (int i = 0; i < Math.max(emitters.size(), old.emitters.size()); i++) {
                Emitter a = i < emitters.size() ? emitters.get(i) : null;
                Emitter b = i < old.emitters.size() ? old.emitters.get(i) : null;
                if (a != null && b != null && a.same(b)) continue;
                if (a != null) keys.add(a.key);
                if (b != null) keys.add(b.key);
                changed = true;
            }
            return changed;
        }

    }

    private static final class Emitter {

        final ICraftingMedium medium;
        final IAEStack<?> key;

        Emitter(ICraftingMedium medium, IAEStack<?> key) {
            this.medium = medium;
            this.key = key.copy()
                .reset();
        }

        boolean same(Emitter other) {
            return medium == other.medium && key.equals(other.key);
        }
    }

    private static final class Option {

        final ICraftingMedium medium;
        final ICraftingPatternDetails pattern;
        final int priority;
        final ItemStack definition;
        final IAEStack<?>[] inputs, outputs;

        Option(ICraftingMedium medium, ICraftingPatternDetails pattern) {
            this.medium = medium;
            this.pattern = pattern;
            priority = pattern.getPriority();
            // These three pinned encoders define their processing contract in the encoded stack.
            // Never infer equivalence of opaque/modded callbacks or crafting recipes from their icon or hash.
            boolean encoded = pattern.getClass() == PatternHelper.class
                || pattern.getClass() == UltimatePatternHelper.class
                || pattern.getClass()
                    .getName()
                    .equals("com.glodblock.github.util.FluidPatternDetails");
            definition = encoded ? pattern.getPattern()
                .copy() : null;
            inputs = encoded ? copy(pattern.getAEInputs()) : null;
            outputs = encoded ? copy(pattern.getAEOutputs()) : null;
        }

        boolean same(Option other) {
            return definition != null && other.definition != null
                && medium == other.medium
                && priority == other.priority
                && pattern.getClass() == other.pattern.getClass()
                && (!pattern.isCraftable() || pattern == other.pattern)
                && pattern.equals(other.pattern)
                && ItemStack.areItemStacksEqual(definition, other.definition)
                && same(inputs, other.inputs)
                && same(outputs, other.outputs);
        }

        private static IAEStack<?>[] copy(IAEStack<?>[] stacks) {
            IAEStack<?>[] copy = stacks.clone();
            for (int i = 0; i < copy.length; i++) if (copy[i] != null) copy[i] = copy[i].copy();
            return copy;
        }

        private static boolean same(IAEStack<?>[] a, IAEStack<?>[] b) {
            if (a.length != b.length) return false;
            for (int i = 0; i < a.length; i++)
                if (!Objects.equals(a[i], b[i]) || a[i] != null && a[i].getStackSize() != b[i].getStackSize())
                    return false;
            return true;
        }
    }
}
