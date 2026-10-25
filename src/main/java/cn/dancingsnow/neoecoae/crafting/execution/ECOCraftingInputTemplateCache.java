package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.InputTemplate;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.PatternRuntime;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ICraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/** CPU-local candidate snapshots. Validity and quantities are deliberately not cached. */
final class ECOCraftingInputTemplateCache {

    private static final int MAX_INPUTS = 256;
    private static final int MAX_TEMPLATES = 4096;
    private final Map<IPatternDetails.IInput, List<InputTemplate>> entries = new Reference2ObjectOpenHashMap<>();
    private final Set<AEKey> knownKeys = new ObjectOpenHashSet<>();
    private ICraftingInventory source;
    private long reloadGeneration = Long.MIN_VALUE;
    private int templateCount;

    List<InputTemplate> get(ICraftingInventory inventory, IPatternDetails.IInput input) {
        long generation = PatternRuntime.reloadGeneration();
        if (source != inventory || reloadGeneration != generation) {
            clear();
            source = inventory;
            reloadGeneration = generation;
        }
        var cached = entries.get(input);
        if (cached != null) return cached;

        var templates = new ArrayList<InputTemplate>();
        for (var stack : input.getPossibleInputs()) {
            for (var key : inventory.findFuzzyTemplates(stack.what())) {
                templates.add(new InputTemplate(key, stack.amount()));
            }
        }
        var result = templates;
        if (result.size() <= MAX_TEMPLATES) {
            if (entries.size() >= MAX_INPUTS || templateCount + result.size() > MAX_TEMPLATES) {
                entries.clear();
                knownKeys.clear();
                templateCount = 0;
            }
            entries.put(input, result);
            for (var template : result) knownKeys.add(template.key());
            templateCount += result.size();
        }
        return result;
    }

    void clear() {
        if (!entries.isEmpty()) entries.clear();
        knownKeys.clear();
        templateCount = 0;
        source = null;
    }

    void inventoryChanged(AEKey key) {
        // Updating the amount of a surviving key leaves AE2's candidate membership and order unchanged.
        if (key != null && source instanceof ListCraftingInventory inventory
            && knownKeys.contains(key)
            && inventory.list.get(key) > 0L) return;
        clear();
    }
}
