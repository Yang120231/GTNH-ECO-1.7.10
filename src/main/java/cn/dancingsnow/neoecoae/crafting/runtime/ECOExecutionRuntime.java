package cn.dancingsnow.neoecoae.crafting.runtime;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey;

/** Persistent ordered execution cursor. Native AE2 remains the owner of items, links and pending outputs. */
public final class ECOExecutionRuntime {

    private final List<NBTTagCompound> patterns = new ArrayList<>();
    private final List<Long> remaining = new ArrayList<>();
    private int cursor;

    public ECOExecutionRuntime(ECOPlanningResult<ECOResourceKey, ICraftingPatternDetails> plan) {
        if (plan.status != ECOPlanningResult.Status.SUCCESS) throw new IllegalArgumentException("Unexecutable plan");
        for (ECOPlanningResult.Step<ECOResourceKey, ICraftingPatternDetails> step : plan.schedule) {
            patterns.add(signature(step.recipe.token));
            remaining.add(step.crafts);
        }
    }

    private ECOExecutionRuntime() {}

    public long allowance(ICraftingPatternDetails pattern) {
        return cursor < patterns.size() && patterns.get(cursor)
            .equals(signature(pattern)) ? remaining.get(cursor) : 0;
    }

    public void dispatched(ICraftingPatternDetails pattern, long count) {
        long allowed = allowance(pattern);
        if (count <= 0 || count > allowed) throw new IllegalStateException("Dispatch violates execution contract");
        remaining.set(cursor, allowed - count);
        if (allowed == count) cursor++;
    }

    public boolean finishedDispatching() {
        return cursor == patterns.size();
    }

    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Version", 1);
        NBTTagList steps = new NBTTagList();
        for (int i = cursor; i < patterns.size(); i++) {
            NBTTagCompound step = new NBTTagCompound();
            step.setTag(
                "Pattern",
                patterns.get(i)
                    .copy());
            step.setLong("Remaining", remaining.get(i));
            steps.appendTag(step);
        }
        data.setTag("Steps", steps);
        return data;
    }

    public static ECOExecutionRuntime read(NBTTagCompound data) {
        if (data.getInteger("Version") != 1) throw new IllegalArgumentException("Unknown execution version");
        NBTTagList steps = data.getTagList("Steps", 10);
        if (steps.tagCount() > 32768) throw new IllegalArgumentException("Execution schedule too large");
        ECOExecutionRuntime runtime = new ECOExecutionRuntime();
        for (int i = 0; i < steps.tagCount(); i++) {
            NBTTagCompound step = steps.getCompoundTagAt(i);
            long count = step.getLong("Remaining");
            if (count <= 0 || !step.hasKey("Pattern", 10)) throw new IllegalArgumentException("Invalid execution step");
            runtime.patterns.add(
                (NBTTagCompound) step.getCompoundTag("Pattern")
                    .copy());
            runtime.remaining.add(count);
        }
        return runtime;
    }

    private static NBTTagCompound signature(ICraftingPatternDetails details) {
        ItemStack pattern = details.getPattern();
        if (pattern == null) throw new IllegalArgumentException("Missing encoded pattern");
        ItemStack copy = pattern.copy();
        copy.stackSize = 1;
        return copy.writeToNBT(new NBTTagCompound());
    }
}
