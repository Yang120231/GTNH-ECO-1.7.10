package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;
import cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks;

/** Captures generic item/fluid IO, accepted candidates and container returns before asynchronous planning. */
public class NativePatternDetails implements IPatternDetails {

    private final ICraftingPatternDetails nativePattern;
    private final AEItemKey definition;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;
    private final GenericStack[] sparse;

    protected NativePatternDetails(ICraftingPatternDetails pattern, World world, Collection<AEKey> available) {
        nativePattern = Objects.requireNonNull(pattern);
        definition = AEItemKey.of(pattern.getPattern());
        IAEStack<?>[] nativeSparse = pattern.getAEInputs();
        if (nativeSparse == null) throw new IllegalArgumentException("Null native sparse inputs");
        sparse = new GenericStack[nativeSparse.length];
        for (int i = 0; i < sparse.length; i++) if (nativeSparse[i] != null) sparse[i] = stack(nativeSparse[i]);
        List<IInput> captured = new ArrayList<>();
        for (IAEStack<?> raw : pattern.getCondensedAEInputs()) {
            GenericStack primary = stack(raw);
            Set<AEKey> choices = new LinkedHashSet<>();
            choices.add(primary.what());
            if (pattern.isCraftable() && pattern.canSubstitute()) {
                for (AEKey candidate : available) {
                    if (candidate.getType() != primary.what()
                        .getType()) continue;
                    boolean valid = true, checked = false;
                    for (int slot = 0; slot < nativeSparse.length; slot++)
                        if (sparse[slot] != null && sparse[slot].what()
                            .equals(primary.what())) {
                                checked = true;
                                if (!pattern.isValidItemForSlot(slot, candidate.toNative(1), world)) {
                                    valid = false;
                                    break;
                                }
                            }
                    if (valid && checked) choices.add(candidate);
                }
            }
            Map<AEKey, AEKey> returns = new HashMap<>();
            if (pattern.isCraftable()) for (AEKey choice : choices) if (choice instanceof AEItemKey) {
                ItemStack selected = ((AEItemKey) choice).toStack(1);
                if (selected.getItem()
                    .hasContainerItem(selected)) {
                    ItemStack remainder = selected.getItem()
                        .getContainerItem(selected);
                    if (!ItemStacks.isEmpty(remainder)) returns.put(choice, AEItemKey.of(remainder));
                }
            }
            GenericStack[] candidates = new GenericStack[choices.size()];
            int index = 0;
            for (AEKey choice : choices) candidates[index++] = new GenericStack(choice, 1);
            captured.add(new FrozenInput(candidates, primary.amount(), Collections.unmodifiableMap(returns)));
        }
        inputs = captured.toArray(new IInput[0]);
        List<GenericStack> produced = new ArrayList<>();
        for (IAEStack<?> output : pattern.getAEOutputs()) if (output != null) produced.add(stack(output));
        outputs = JdkCompat.listCopyOf(produced);
    }

    public static NativePatternDetails capture(ICraftingPatternDetails pattern, World world,
        Collection<AEKey> candidates) {
        if (pattern.isCraftable() && pattern.getClass() == appeng.helpers.PatternHelper.class)
            return new NativeCraftingPattern(pattern, world, candidates);
        return !pattern.isCraftable() && pattern.getClass() == appeng.helpers.PatternHelper.class
            ? new AEProcessingPattern(pattern, world, candidates)
            : new NativePatternDetails(pattern, world, candidates);
    }

    private static GenericStack stack(IAEStack<?> stack) {
        AEKey key = AEKey.fromNative(stack);
        if (key == null || stack.getStackSize() <= 0)
            throw new IllegalArgumentException("Invalid native pattern stack");
        return new GenericStack(key, stack.getStackSize());
    }

    public final ICraftingPatternDetails nativeDetails() {
        return nativePattern;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return !nativePattern.isCraftable() && nativePattern.getClass() == appeng.helpers.PatternHelper.class;
    }

    public final GenericStack[] getSparseInputs() {
        return sparse.clone();
    }

    @Override
    public final Class<?> implementationClass() {
        return nativePattern.getClass();
    }

    @Override
    public final AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public final IInput[] getInputs() {
        return inputs.clone();
    }

    @Override
    public final List<GenericStack> getOutputs() {
        return outputs;
    }

    private static final class FrozenInput implements IInput {

        private final GenericStack[] choices;
        private final long multiplier;
        private final Map<AEKey, AEKey> returns;

        FrozenInput(GenericStack[] choices, long multiplier, Map<AEKey, AEKey> returns) {
            this.choices = choices;
            this.multiplier = multiplier;
            this.returns = returns;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return choices.clone();
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public AEKey getRemainingKey(AEKey input) {
            return returns.get(input);
        }
    }
}
