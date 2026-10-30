package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingJobV2;
import appeng.crafting.v2.resolvers.CraftableItemResolver;
import appeng.crafting.v2.resolvers.CraftingTask;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.jdk.JdkCompat;

/**
 * The crafting graph of a plan AE2's own planner made, in the snapshot form the ECO plan tree reads, so ECO and
 * natively planned requests show in the same tree. Materials carry the amounts of the confirmation table; patterns
 * are the plan's pattern tasks with their total executions, relationships per execution as in ECO's snapshot.
 */
public final class NativeCraftingGraphSnapshots {

    private NativeCraftingGraphSnapshots() {}

    private static final class Material {

        final AEKey key;
        BigInteger requested = BigInteger.ZERO;
        BigInteger stored = BigInteger.ZERO;
        BigInteger missing = BigInteger.ZERO;
        BigInteger craft = BigInteger.ZERO;
        BigInteger consumed = BigInteger.ZERO;
        BigInteger produced = BigInteger.ZERO;

        Material(AEKey key) {
            this.key = key;
        }

        CraftingGraphSnapshot.MaterialNode freeze(int id) {
            BigInteger demand = requested.signum() > 0 ? requested
                : stored.add(missing)
                    .max(consumed);
            CraftingGraphSnapshot.MaterialStatus status = missing.signum() > 0
                ? CraftingGraphSnapshot.MaterialStatus.MISSING
                : craft.signum() > 0 ? CraftingGraphSnapshot.MaterialStatus.CRAFTING
                    : CraftingGraphSnapshot.MaterialStatus.SATISFIED;
            return new CraftingGraphSnapshot.MaterialNode(
                id,
                key,
                clamp(demand),
                clamp(stored),
                clamp(craft),
                clamp(missing),
                status,
                demand.toString(),
                stored.toString(),
                craft.toString(),
                missing.toString(),
                consumed.toString(),
                produced.toString());
        }
    }

    public static CraftingGraphSnapshot build(ICraftingJob job, BaseActionSource source) {
        Map<AEKey, Material> materials = new LinkedHashMap<>();
        AEKey rootKey = AEKey.fromNative(job.getOutput());
        if (rootKey != null) materials.computeIfAbsent(rootKey, Material::new).requested = BigInteger.valueOf(
            Math.max(
                0,
                job.getOutput()
                    .getStackSize()));
        for (ECOCraftConfirmReport.Row row : NativeCraftConfirmReports.rows(job, source)) {
            Material material = materials.computeIfAbsent(row.key, Material::new);
            material.stored = row.stored;
            material.missing = row.missing;
            material.craft = row.craft;
        }

        Map<ICraftingPatternDetails, Long> executions = new IdentityHashMap<>();
        List<ICraftingPatternDetails> order = new ArrayList<>();
        CraftingContext context = job instanceof CraftingJobV2 v2 ? v2.getContext() : null;
        if (context != null) for (CraftingTask task : context.getResolvedTasks()) {
            if (!(task instanceof CraftableItemResolver.CraftFromPatternTask craft)) continue;
            long done = craft.getTotalCraftsDone();
            if (done <= 0 || craft.pattern == null) continue;
            if (!executions.containsKey(craft.pattern)) order.add(craft.pattern);
            executions.merge(craft.pattern, done, Long::sum);
        }
        for (ICraftingPatternDetails pattern : order) {
            BigInteger times = BigInteger.valueOf(executions.get(pattern));
            for (IAEStack<?> input : inputs(pattern)) {
                Material material = materials.computeIfAbsent(AEKey.fromNative(input), Material::new);
                material.consumed = material.consumed.add(times.multiply(BigInteger.valueOf(input.getStackSize())));
            }
            for (IAEStack<?> output : outputs(pattern)) {
                Material material = materials.computeIfAbsent(AEKey.fromNative(output), Material::new);
                material.produced = material.produced.add(times.multiply(BigInteger.valueOf(output.getStackSize())));
            }
        }

        Map<AEKey, Integer> ids = new LinkedHashMap<>();
        List<CraftingGraphSnapshot.MaterialNode> nodes = new ArrayList<>(materials.size());
        for (Material material : materials.values()) {
            ids.put(material.key, nodes.size());
            nodes.add(material.freeze(nodes.size()));
        }
        List<CraftingGraphSnapshot.PatternNode> patterns = new ArrayList<>(order.size());
        List<CraftingGraphSnapshot.Edge> edges = new ArrayList<>();
        for (ICraftingPatternDetails pattern : order) {
            int patternId = CraftingGraphSnapshotFactory.patternNodeId(patterns.size());
            List<CraftingGraphSnapshot.Relationship> in = relationships(inputs(pattern), ids);
            List<CraftingGraphSnapshot.Relationship> out = relationships(outputs(pattern), ids);
            patterns.add(
                new CraftingGraphSnapshot.PatternNode(
                    patternId,
                    displayIdentity(pattern, out, nodes),
                    in,
                    out,
                    executions.get(pattern),
                    CraftingGraphSnapshot.CandidateStatus.SELECTED,
                    null,
                    -1));
            for (int i = 0; i < out.size(); i++) edges.add(
                new CraftingGraphSnapshot.Edge(
                    out.get(i)
                        .materialNodeId(),
                    patternId,
                    out.get(i)
                        .amount(),
                    i == 0 ? CraftingGraphSnapshot.EdgeKind.PATTERN_OUTPUT : CraftingGraphSnapshot.EdgeKind.BYPRODUCT,
                    true));
            for (CraftingGraphSnapshot.Relationship input : in) edges.add(
                new CraftingGraphSnapshot.Edge(
                    patternId,
                    input.materialNodeId(),
                    input.amount(),
                    CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT,
                    true));
        }
        Integer rootId = rootKey == null ? null : ids.get(rootKey);
        return new CraftingGraphSnapshot(
            rootId == null ? -1 : rootId,
            nodes,
            patterns,
            edges,
            JdkCompat.listOf(),
            JdkCompat.listOf(),
            new CraftingGraphSnapshot.Summary("NATIVE", nodes.size(), patterns.size(), edges.size(), 0, 0L));
    }

    private static List<IAEStack<?>> inputs(ICraftingPatternDetails pattern) {
        return present(pattern.getCondensedAEInputs());
    }

    private static List<IAEStack<?>> outputs(ICraftingPatternDetails pattern) {
        return present(pattern.getCondensedAEOutputs());
    }

    private static List<IAEStack<?>> present(IAEStack<?>[] stacks) {
        List<IAEStack<?>> result = new ArrayList<>();
        if (stacks != null) for (IAEStack<?> stack : stacks)
            if (stack != null && stack.getStackSize() > 0 && AEKey.fromNative(stack) != null) result.add(stack);
        return result;
    }

    private static List<CraftingGraphSnapshot.Relationship> relationships(List<IAEStack<?>> stacks,
        Map<AEKey, Integer> ids) {
        List<CraftingGraphSnapshot.Relationship> result = new ArrayList<>(stacks.size());
        for (IAEStack<?> stack : stacks) {
            Integer id = ids.get(AEKey.fromNative(stack));
            if (id != null) result.add(new CraftingGraphSnapshot.Relationship(id, stack.getStackSize()));
        }
        return result;
    }

    private static String displayIdentity(ICraftingPatternDetails pattern,
        List<CraftingGraphSnapshot.Relationship> outputs, List<CraftingGraphSnapshot.MaterialNode> nodes) {
        String name = pattern.getClass()
            .getSimpleName();
        return outputs.isEmpty() ? name
            : name + ':'
                + nodes.get(
                    outputs.get(0)
                        .materialNodeId())
                    .key();
    }

    private static long clamp(BigInteger value) {
        return value.bitLength() < 63 ? value.longValue() : Long.MAX_VALUE;
    }
}
