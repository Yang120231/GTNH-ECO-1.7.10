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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableList;

import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf;

/**
 * A crafting plan intended to be sent to the client.
 */
public class CraftingPlanSummary {

    /**
     * @see ICraftingPlan#bytes()
     */
    private final long usedBytes;

    /**
     * @see ICraftingPlan#simulation()
     */
    private final boolean simulation;

    private final List<CraftingPlanSummaryEntry> entries;

    public CraftingPlanSummary(long usedBytes, boolean simulation, List<CraftingPlanSummaryEntry> entries) {
        this.usedBytes = usedBytes;
        this.simulation = simulation;
        this.entries = entries;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public boolean isSimulation() {
        return simulation;
    }

    public List<CraftingPlanSummaryEntry> getEntries() {
        return entries;
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(usedBytes);
        buffer.writeBoolean(simulation);
        buffer.writeVarInt(entries.size());
        for (CraftingPlanSummaryEntry entry : entries) {
            entry.write(buffer);
        }
    }

    public static CraftingPlanSummary read(RegistryFriendlyByteBuf buffer) {

        long bytesUsed = buffer.readVarLong();
        boolean simulation = buffer.readBoolean();
        int entryCount = buffer.readVarInt();
        ImmutableList.Builder<CraftingPlanSummaryEntry> entries = ImmutableList.builder();
        for (int i = 0; i < entryCount; i++) {
            entries.add(CraftingPlanSummaryEntry.read(buffer));
        }

        return new CraftingPlanSummary(bytesUsed, simulation, entries.build());
    }

    private static class KeyStats {

        public long stored;
    }

    public static Map<AEKey, BigInteger> craftedAmounts(ICraftingPlan job) {
        Map<AEKey, BigInteger> amounts = new HashMap<>();
        for (var emitted : job.emittedItems())
            amounts.merge(emitted.getKey(), BigInteger.valueOf(emitted.getLongValue()), BigInteger::add);
        for (var entry : job.patternTimes()
            .entrySet()) {
            BigInteger times = BigInteger.valueOf(entry.getValue());
            for (var output : entry.getKey()
                .getOutputs())
                amounts.merge(
                    output.what(),
                    BigInteger.valueOf(output.amount())
                        .multiply(times),
                    BigInteger::add);
        }
        return amounts;
    }

    /**
     * Creates a plan summary from the given planning result.
     *
     * @param grid         The grid used to determine the amount of items already stored.
     * @param actionSource The action source used to determine the amount of items already stored.
     */
    public static CraftingPlanSummary fromJob(GridView grid, IActionSource actionSource, ICraftingPlan job) {
        return fromJob(grid, actionSource, job, craftedAmounts(job));
    }

    public static CraftingPlanSummary fromJob(GridView grid, IActionSource actionSource, ICraftingPlan job,
        Map<AEKey, BigInteger> craftedAmounts) {
        var plan = new HashMap<AEKey, KeyStats>() {

            private KeyStats mapping(AEKey key) {
                Objects.requireNonNull(key, "Key may not be null");

                return computeIfAbsent(key, k -> new KeyStats());
            }
        };

        for (var used : job.usedItems()) {
            plan.mapping(used.getKey()).stored += used.getLongValue();
        }
        for (var missing : job.missingItems()) {
            plan.mapping(missing.getKey()).stored += missing.getLongValue();
        }
        for (var emitted : job.emittedItems()) {
            var entry = plan.mapping(emitted.getKey());
            entry.stored += emitted.getLongValue();
        }
        craftedAmounts.keySet()
            .forEach(plan::mapping);

        var entries = new ArrayList<CraftingPlanSummaryEntry>();

        var storage = grid.getStorageService()
            .getInventory();
        var crafting = grid.getCraftingService();

        for (var out : plan.entrySet()) {
            long missingAmount;
            long storedAmount;
            if (job.simulation() && !crafting.canEmitFor(out.getKey())) {
                storedAmount = storage.extract(out.getKey(), out.getValue().stored, Actionable.SIMULATE, actionSource);
                missingAmount = out.getValue().stored - storedAmount;
            } else {
                storedAmount = out.getValue().stored;
                missingAmount = 0;
            }
            long craftAmount = craftedAmounts.getOrDefault(out.getKey(), BigInteger.ZERO)
                .min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValueExact();

            entries.add(new CraftingPlanSummaryEntry(out.getKey(), missingAmount, storedAmount, craftAmount));

        }

        Collections.sort(entries);

        return new CraftingPlanSummary(
            job.bytes(),
            job.simulation(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listCopyOf(entries));

    }

}
