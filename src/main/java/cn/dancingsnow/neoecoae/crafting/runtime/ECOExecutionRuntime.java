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
    private final List<Boolean> sequential = new ArrayList<>();
    private cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionRuntime.DispatchCandidate offered;
    private int cursor;
    private cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionRuntime phased;
    private cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionBinding binding;
    private cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionPlanCodec.Codec codec;

    public ECOExecutionRuntime(cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionPlan plan,
        cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionBinding binding,
        cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionPlanCodec.Codec codec) {
        this.binding = binding;
        this.codec = codec;
        this.phased = new cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionRuntime(
            plan,
            binding.patterns(),
            binding.progress());
    }

    public boolean phased() {
        return phased != null;
    }

    public void accepted(ICraftingPatternDetails pattern, long count, java.util.Map<Object, Long>[] inputs) {
        if (phased == null) {
            dispatched(pattern, count);
            return;
        }
        binding.refresh();
        var candidate = offered;
        if (candidate == null || candidate.pattern() != pattern)
            throw new IllegalStateException("Task is not ready for dispatch");
        // GTNH has already decremented batch extras, but decrements its first craft after pushPattern returns.
        var progress = binding.progress()[candidate.taskId()];
        if (progress.value < 1) throw new IllegalStateException("Missing first-craft progress");
        progress.value--;
        phased.onAccepted(candidate, count, inputs);
    }

    private cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionRuntime.DispatchCandidate selected(
        ICraftingPatternDetails pattern) {
        for (var candidate : phased.candidates()) if (candidate.pattern() == pattern) return candidate;
        return null;
    }

    public ECOExecutionRuntime(ECOPlanningResult<ECOResourceKey, ICraftingPatternDetails> plan) {
        if (plan.status != ECOPlanningResult.Status.SUCCESS) throw new IllegalArgumentException("Unexecutable plan");
        for (ECOPlanningResult.Step<ECOResourceKey, ICraftingPatternDetails> step : plan.schedule) {
            patterns.add(signature(step.recipe.token));
            remaining.add(step.crafts);
            sequential.add(step.sequential);
        }
    }

    private ECOExecutionRuntime() {}

    public long allowance(ICraftingPatternDetails pattern) {
        if (phased != null) {
            binding.refresh();
            var candidate = selected(pattern);
            offered = candidate;
            return candidate == null ? 0 : candidate.maxDispatchCount();
        }
        return cursor < patterns.size() && patterns.get(cursor)
            .equals(signature(pattern))
                ? (sequential.get(cursor) ? Math.min(1L, remaining.get(cursor)) : remaining.get(cursor))
                : 0;
    }

    public void dispatched(ICraftingPatternDetails pattern, long count) {
        long allowed = allowance(pattern);
        if (count <= 0 || count > allowed) throw new IllegalStateException("Dispatch violates execution contract");
        long left = remaining.get(cursor) - count;
        remaining.set(cursor, left);
        if (left == 0) cursor++;
    }

    public boolean finishedDispatching() {
        if (phased != null) {
            binding.refresh();
            return phased.isComplete();
        }
        return cursor == patterns.size();
    }

    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        if (phased != null) {
            binding.refresh();
            data.setInteger("Version", 2);
            data.setTag(
                "Plan",
                cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionPlanCodec
                    .writeExecutionPlan(phased.plan(), codec));
            data.setTag(
                "Output",
                codec.write(
                    phased.plan()
                        .signature()
                        .finalWhat(),
                    phased.plan()
                        .signature()
                        .finalAmount()));
            data.setTag("Runtime", phased.write(codec));
            return data;
        }
        data.setInteger("Version", 1);
        NBTTagList steps = new NBTTagList();
        for (int i = cursor; i < patterns.size(); i++) {
            NBTTagCompound step = new NBTTagCompound();
            step.setTag(
                "Pattern",
                patterns.get(i)
                    .copy());
            step.setLong("Remaining", remaining.get(i));
            step.setBoolean("Sequential", sequential.get(i));
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
            runtime.sequential.add(step.getBoolean("Sequential"));
        }
        return runtime;
    }

    public static ECOExecutionRuntime read(NBTTagCompound data, Iterable<ICraftingPatternDetails> tasks,
        java.util.function.ToLongFunction<ICraftingPatternDetails> remaining,
        cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionPlanCodec.Codec codec) {
        if (data.getInteger("Version") == 1) return read(data);
        if (data.getInteger("Version") != 2) throw new IllegalArgumentException("Unknown execution version");
        var plan = cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionPlanCodec
            .readExecutionPlan(data.getCompoundTag("Plan"), codec, codec.read(data.getCompoundTag("Output")));
        var binding = new cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionBinding(
            plan,
            tasks,
            remaining,
            false,
            codec);
        ECOExecutionRuntime result = new ECOExecutionRuntime(plan, binding, codec);
        result.phased = cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOExecutionRuntime
            .read(plan, binding.patterns(), binding.progress(), data.getCompoundTag("Runtime"), codec);
        return result;
    }

    private static NBTTagCompound signature(ICraftingPatternDetails details) {
        ItemStack pattern = details.getPattern();
        if (pattern == null) throw new IllegalArgumentException("Missing encoded pattern");
        ItemStack copy = pattern.copy();
        copy.stackSize = 1;
        return copy.writeToNBT(new NBTTagCompound());
    }
}
