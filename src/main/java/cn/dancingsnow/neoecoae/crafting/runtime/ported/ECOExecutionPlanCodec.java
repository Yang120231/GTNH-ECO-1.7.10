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

package cn.dancingsnow.neoecoae.crafting.runtime.ported;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.ported.result.PlannedInputAllocation;

/** Plan persistence ported from 1.21.1 ExecutingCraftingJob; registry operations stay at the boundary. */
public final class ECOExecutionPlanCodec {

    private ECOExecutionPlanCodec() {}

    public interface Codec extends ECOExecutionRuntime.ResourceCodec {

        NBTTagCompound writePattern(ICraftingPatternDetails pattern);

        ICraftingPatternDetails readPattern(NBTTagCompound data);

        PlanIdentity.PatternIdentity identity(ICraftingPatternDetails pattern);

        default ECOExecutionPlan.PatternRuntimeInfo runtimeInfo(ICraftingPatternDetails pattern) {
            return ECOExecutionPlan.PatternRuntimeInfo.from(pattern);
        }
    }

    public static NBTTagCompound writeExecutionPlan(ECOExecutionPlan plan, Codec codec) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(
            "mode",
            plan.mode()
                .name());

        NBTTagList taskTags = new NBTTagList();
        for (var task : plan.tasks()) {
            if (task.pattern()
                .getPattern() == null) {
                throw new IllegalStateException("Cannot persist an execution task without a pattern definition");
            }
            NBTTagCompound taskTag = new NBTTagCompound();
            taskTag.setInteger("id", task.id());
            taskTag.setLong("total", task.totalCount());
            taskTag.setInteger("phase", task.phaseIndex());
            taskTag.setString(
                "kind",
                task.kind()
                    .name());
            taskTag.setTag("pattern", codec.writePattern(task.pattern()));
            NBTTagList allocations = new NBTTagList();
            for (var allocation : task.inputAllocations()) {
                NBTTagCompound allocationTag = new NBTTagCompound();
                allocationTag.setInteger("slot", allocation.slot());
                NBTTagList runs = new NBTTagList();
                for (var run : allocation.runs()) {
                    NBTTagCompound runTag = codec.write(run.key(), run.amount());
                    runTag.setLong("crafts", run.crafts());
                    runs.appendTag(runTag);
                }
                allocationTag.setTag("runs", runs);
                allocations.appendTag(allocationTag);
            }
            taskTag.setTag("inputAllocations", allocations);
            taskTags.appendTag(taskTag);
        }
        data.setTag("tasks", taskTags);

        NBTTagList phaseTags = new NBTTagList();
        for (var phase : plan.phases()) {
            NBTTagCompound phaseTag = new NBTTagCompound();
            phaseTag.setInteger("index", phase.index());
            phaseTag.setInteger("component", phase.componentId());
            phaseTag.setString(
                "type",
                phase.type()
                    .name());
            phaseTag.setIntArray(
                "tasks",
                phase.taskIds()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .toArray());
            phaseTag.setIntArray(
                "dependencies",
                phase.dependencies()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .toArray());

            NBTTagList steps = new NBTTagList();
            for (var step : phase.steps()) {
                NBTTagCompound stepTag = new NBTTagCompound();
                stepTag.setInteger("task", step.taskId());
                stepTag.setLong("count", step.count());
                steps.appendTag(stepTag);
            }
            phaseTag.setTag("steps", steps);

            NBTTagList dynamic = new NBTTagList();
            for (var entry : phase.dynamicFirings()
                .entrySet()) {
                NBTTagCompound firing = new NBTTagCompound();
                firing.setInteger("task", entry.getKey());
                firing.setLong("count", entry.getValue());
                dynamic.appendTag(firing);
            }
            phaseTag.setTag("dynamic", dynamic);

            NBTTagList seeds = new NBTTagList();
            for (var entry : phase.initialSeed()
                .entrySet()) {
                seeds.appendTag(codec.write(entry.getKey(), entry.getValue()));
            }
            phaseTag.setTag("initialSeed", seeds);
            phaseTags.appendTag(phaseTag);
        }
        data.setTag("phases", phaseTags);
        return data;
    }

    public static ECOExecutionPlan readExecutionPlan(NBTTagCompound data, Codec codec, GenericStack finalOutput) {
        ExecutionMode mode = ExecutionMode.valueOf(data.getString("mode"));
        NBTTagList taskTags = data.getTagList("tasks", 10);
        List<ECOExecutionPlan.TaskSpec> tasks = new ArrayList<>(taskTags.tagCount());
        Map<Integer, ICraftingPatternDetails> patternsById = new HashMap<>();
        for (int i = 0; i < taskTags.tagCount(); i++) {
            NBTTagCompound taskTag = taskTags.getCompoundTagAt(i);
            int id = taskTag.getInteger("id");
            if (id != i) throw new IllegalArgumentException("Persisted execution task ids are not dense");
            ICraftingPatternDetails pattern = codec.readPattern(taskTag.getCompoundTag("pattern"));
            if (pattern == null) throw new IllegalArgumentException("Persisted execution pattern cannot be decoded");
            PlanIdentity.PatternIdentity identity = codec.identity(pattern);
            if (identity == null) throw new IllegalArgumentException("Persisted execution pattern has no identity");
            patternsById.put(id, pattern);
            List<PlannedInputAllocation> allocations = new ArrayList<>();
            NBTTagList allocationTags = taskTag.getTagList("inputAllocations", 10);
            for (int allocationIndex = 0; allocationIndex < allocationTags.tagCount(); allocationIndex++) {
                NBTTagCompound allocationTag = allocationTags.getCompoundTagAt(allocationIndex);
                List<PlannedInputAllocation.Run> runs = new ArrayList<>();
                NBTTagList runTags = allocationTag.getTagList("runs", 10);
                for (int runIndex = 0; runIndex < runTags.tagCount(); runIndex++) {
                    NBTTagCompound runTag = runTags.getCompoundTagAt(runIndex);
                    GenericStack stack = codec.read(runTag);
                    long crafts = runTag.getLong("crafts");
                    if (stack == null) throw new IllegalArgumentException("Invalid persisted input allocation");
                    runs.add(new PlannedInputAllocation.Run(stack.what(), stack.amount(), crafts));
                }
                allocations.add(new PlannedInputAllocation(allocationTag.getInteger("slot"), runs));
            }
            tasks.add(
                new ECOExecutionPlan.TaskSpec(
                    id,
                    identity,
                    pattern,
                    codec.runtimeInfo(pattern),
                    taskTag.getLong("total"),
                    taskTag.getInteger("phase"),
                    ECOExecutionPlan.TaskKind.valueOf(taskTag.getString("kind")),
                    allocations));
        }

        NBTTagList phaseTags = data.getTagList("phases", 10);
        List<ECOExecutionPlan.PhaseSpec> phases = new ArrayList<>(phaseTags.tagCount());
        List<ECOExecutionSchedule.ComponentExecutionPhase> schedulePhases = new ArrayList<>(phaseTags.tagCount());
        List<ECOExecutionSchedule.PhaseDependency> dependencies = new ArrayList<>();
        for (int i = 0; i < phaseTags.tagCount(); i++) {
            NBTTagCompound phaseTag = phaseTags.getCompoundTagAt(i);
            int index = phaseTag.getInteger("index");
            if (index != i) throw new IllegalArgumentException("Persisted execution phase ids are not dense");
            ECOExecutionSchedule.Type type = ECOExecutionSchedule.Type.valueOf(phaseTag.getString("type"));
            List<Integer> taskIds = Arrays.stream(phaseTag.getIntArray("tasks"))
                .boxed()
                .collect(java.util.stream.Collectors.toList());
            List<Integer> phaseDependencies = Arrays.stream(phaseTag.getIntArray("dependencies"))
                .boxed()
                .collect(java.util.stream.Collectors.toList());
            List<ECOExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            NBTTagList stepTags = phaseTag.getTagList("steps", 10);
            for (int stepIndex = 0; stepIndex < stepTags.tagCount(); stepIndex++) {
                NBTTagCompound step = stepTags.getCompoundTagAt(stepIndex);
                steps.add(new ECOExecutionPlan.ExecutionStep(step.getInteger("task"), step.getLong("count")));
            }
            Map<Integer, Long> dynamic = new LinkedHashMap<>();
            NBTTagList dynamicTags = phaseTag.getTagList("dynamic", 10);
            for (int dynamicIndex = 0; dynamicIndex < dynamicTags.tagCount(); dynamicIndex++) {
                NBTTagCompound firing = dynamicTags.getCompoundTagAt(dynamicIndex);
                dynamic.put(firing.getInteger("task"), firing.getLong("count"));
            }
            Map<Object, Long> seed = new LinkedHashMap<>();
            NBTTagList seedTags = phaseTag.getTagList("initialSeed", 10);
            for (int seedIndex = 0; seedIndex < seedTags.tagCount(); seedIndex++) {
                GenericStack stack = codec.read(seedTags.getCompoundTagAt(seedIndex));
                if (stack == null || stack.amount() <= 0L) throw new IllegalArgumentException("Invalid persisted seed");
                seed.put(stack.what(), stack.amount());
            }
            phases.add(
                new ECOExecutionPlan.PhaseSpec(
                    index,
                    phaseTag.getInteger("component"),
                    type,
                    taskIds,
                    steps,
                    phaseDependencies,
                    dynamic,
                    seed));

            LinkedHashSet<ICraftingPatternDetails> patternSet = new LinkedHashSet<>();
            for (int taskId : taskIds) patternSet.add(patternsById.get(taskId));
            schedulePhases.add(
                new ECOExecutionSchedule.ComponentExecutionPhase(
                    phaseTag.getInteger("component"),
                    type,
                    patternSet,
                    Collections.emptyList()));
            for (int dependency : phaseDependencies) {
                dependencies.add(new ECOExecutionSchedule.PhaseDependency(dependency, index));
            }
        }

        Map<PlanIdentity.PatternIdentity, Long> signatureTasks = new LinkedHashMap<>();
        for (var task : tasks) signatureTasks.put(task.identity(), task.totalCount());
        if (finalOutput == null) throw new IllegalArgumentException("Persisted execution job has no final output");
        PlanIdentity.Signature signature = new PlanIdentity.Signature(
            finalOutput.what(),
            finalOutput.amount(),
            signatureTasks,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap());
        return new ECOExecutionPlan(
            signature,
            mode,
            tasks,
            phases,
            new ECOExecutionSchedule(schedulePhases, dependencies));
    }

}
