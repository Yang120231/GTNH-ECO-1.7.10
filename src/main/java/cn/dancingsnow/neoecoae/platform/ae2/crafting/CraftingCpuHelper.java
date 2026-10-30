/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package cn.dancingsnow.neoecoae.platform.ae2.crafting;

import java.util.ArrayList;

import net.minecraft.world.World;

import com.google.common.collect.Iterables;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ICraftingInventory;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

public class CraftingCpuHelper {

    public static double calculatePatternPower(KeyCounter[] craftingContainer) {
        double sum = 0;
        for (var itemHolder : craftingContainer)
            // GTNH CraftingCPUCluster charges stackSize / getAmountPerUnit() (1000 per bucket for fluids).
            for (var input : itemHolder) sum += ((double) input.getLongValue()) / input.getKey()
                .getAmountPerUnit();
        return sum;
    }

    public static cn.dancingsnow.neoecoae.platform.nbt.CompoundTag generateLinkData(java.util.UUID id,
        boolean standalone, boolean requester) {
        return CraftingLink.generateData(id, standalone, requester);
    }

    /** Admission keeps every physically extracted remainder in CPU custody until rollback succeeds. */
    public static cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack tryExtractInitialItems(ICraftingPlan plan,
        cn.dancingsnow.neoecoae.platform.ae2.grid.GridView grid,
        cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory.ListCraftingInventory cpuInventory,
        cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource source) {
        var storage = grid.getStorageService()
            .getInventory();
        for (var entry : plan.usedItems()) {
            var key = entry.getKey();
            long wanted = entry.getLongValue();
            long extracted = storage.extract(key, wanted, Actionable.MODULATE, source);
            if (extracted < 0 || extracted > wanted) throw new IllegalStateException("Invalid initial extraction");
            cpuInventory.insert(key, extracted, Actionable.MODULATE);
            if (extracted < wanted) {
                for (var retained : cpuInventory.list.copy()) {
                    long inserted = storage
                        .insert(retained.getKey(), retained.getLongValue(), Actionable.MODULATE, source);
                    if (inserted < 0 || inserted > retained.getLongValue())
                        throw new IllegalStateException("Invalid initial rollback insertion");
                    cpuInventory.extract(retained.getKey(), inserted, Actionable.MODULATE);
                }
                return new cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack(key, wanted - extracted);
            }
        }
        return null;
    }

    public static void reinjectPatternInputs(ICraftingInventory sourceInv, KeyCounter[] inputHolder) {
        for (var list : inputHolder) {
            // List may be null if we failed to extract some of the pattern's inputs.
            if (list != null) {
                for (var entry : list) {
                    sourceInv.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                }
            }
        }
    }

    public static Iterable<InputTemplate> getValidItemTemplates(ICraftingInventory inv, IPatternDetails.IInput input,
        World level) {
        var possibleInputs = input.getPossibleInputs();

        var substitutes = new ArrayList<InputTemplate>(possibleInputs.length);

        for (var stack : possibleInputs) {
            for (var fuzz : inv.findFuzzyTemplates(stack.what())) {
                substitutes.add(new InputTemplate(fuzz, stack.amount()));
            }
        }

        return Iterables.filter(substitutes, stack -> input.isValid(stack.key(), level));
    }

    public static long extractTemplates(ICraftingInventory inv, InputTemplate template, long multiplier) {
        long maxTotal = template.amount() * multiplier;
        // Extract as much as possible.
        var extracted = inv.extract(template.key(), maxTotal, Actionable.SIMULATE);
        if (extracted == 0) return 0;
        // Adjust to have a whole number of templates.
        multiplier = extracted / template.amount();
        maxTotal = template.amount() * multiplier;
        if (maxTotal == 0) return 0;
        extracted = inv.extract(template.key(), maxTotal, Actionable.MODULATE);
        if (extracted == 0 || extracted != maxTotal) {
            throw new IllegalStateException("Failed to correctly extract whole number. Invalid simulation!");
        }
        return multiplier;
    }

    private CraftingCpuHelper() {}
}
