package cn.dancingsnow.neoecoae.crafting.ae2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.MECraftingInventory;
import appeng.me.cache.CraftingGridCache;
import cn.dancingsnow.neoecoae.crafting.planner.ECORecipe;
import cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey;

/** Captured on the requesting server thread. The solver never walks a live grid. */
public final class ECOCraftingSnapshot {

    public final List<ECORecipe<ECOResourceKey, ICraftingPatternDetails>> recipes = new ArrayList<>();
    public final Map<ECOResourceKey, Long> inventory = new LinkedHashMap<>();
    public final MECraftingInventory storage;
    public boolean cyclePlanningEnabled = true;

    public ECOCraftingSnapshot(CraftingGridCache crafting, MECraftingInventory storage, ECOResourceKey goal) {
        this(crafting, storage, goal, false);
    }

    public ECOCraftingSnapshot(CraftingGridCache crafting, MECraftingInventory storage, ECOResourceKey goal,
        boolean ignoreSubstitutions) {
        this.storage = storage;
        Map<ECOResourceKey, List<ICraftingPatternDetails>> index = new LinkedHashMap<>();
        crafting.getCraftingMultiPatterns()
            .forEach((stack, patterns) -> index.put(new ECOResourceKey(stack), patterns));
        Set<ECOResourceKey> seen = new HashSet<>();
        Set<ICraftingPatternDetails> captured = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<ECOResourceKey> pending = new ArrayDeque<>();
        pending.add(goal);
        while (!pending.isEmpty()) {
            ECOResourceKey key = pending.removeFirst();
            if (!seen.add(key)) continue;
            if (seen.size() > 16384) throw new UnsupportedOperationException("Crafting graph exceeds snapshot limit");
            IAEStack<?> available = storage.extractItems((IAEStack) key.stack(Long.MAX_VALUE), Actionable.SIMULATE);
            if (available != null && available.getStackSize() > 0) inventory.put(key, available.getStackSize());
            if (crafting.canEmitFor(key.stack(1)))
                throw new UnsupportedOperationException("Emitter requires native planning");
            List<ICraftingPatternDetails> patterns = index.get(key);
            if (patterns == null) continue;
            for (ICraftingPatternDetails pattern : patterns) {
                if (!captured.add(pattern)) continue;
                if ((!ignoreSubstitutions && pattern.canSubstitute()) || pattern.isInputOnly())
                    throw new UnsupportedOperationException(
                        "Substitution or input-only semantics require native planning");
                Map<ECOResourceKey, Long> inputs = amounts(pattern.getCondensedAEInputs());
                Map<ECOResourceKey, Long> outputs = amounts(pattern.getCondensedAEOutputs());
                if (pattern.isCraftable()) {
                    for (IAEStack<?> input : pattern.getAEInputs()) {
                        if (input instanceof appeng.api.storage.data.IAEItemStack) {
                            net.minecraft.item.ItemStack item = ((appeng.api.storage.data.IAEItemStack) input)
                                .getItemStack();
                            if (item.getItem()
                                .hasContainerItem(item))
                                throw new UnsupportedOperationException("Container recipe requires native planning");
                        }
                    }
                }
                recipes.add(new ECORecipe<>(pattern, inputs, outputs));
                pending.addAll(inputs.keySet());
            }
        }
        recipes.sort((left, right) -> Integer.compare(right.token.getPriority(), left.token.getPriority()));
    }

    public static Map<ECOResourceKey, Long> amounts(IAEStack<?>[] stacks) {
        Map<ECOResourceKey, Long> amounts = new LinkedHashMap<>();
        if (stacks == null) throw new UnsupportedOperationException("Pattern has no stack description");
        for (IAEStack<?> stack : stacks) {
            if (stack == null) continue;
            if (stack.getStackSize() <= 0) throw new UnsupportedOperationException("Nonpositive pattern amount");
            ECOResourceKey key = new ECOResourceKey(stack);
            amounts.put(key, Math.addExact(amounts.getOrDefault(key, 0L), stack.getStackSize()));
        }
        return amounts;
    }
}
