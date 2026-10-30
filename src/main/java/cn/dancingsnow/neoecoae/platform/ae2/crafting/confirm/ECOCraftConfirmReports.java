package cn.dancingsnow.neoecoae.platform.ae2.crafting.confirm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.ExactKeyAmount;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlanSummary;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.platform.ae2.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.platform.ae2.grid.GridView;
import cn.dancingsnow.neoecoae.platform.ae2.grid.IActionSource;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;

/**
 * Builds the ECO report of one confirmed plan on the server, mirroring what 1.21's confirmation menu mixin derives
 * from the planning result: status, timing, theoretical bytes, diagnostics, cycle members and the material table.
 */
public final class ECOCraftConfirmReports {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae");

    private ECOCraftConfirmReports() {}

    /** Header and rows built from one plan. */
    public static final class Built {

        public final ECOCraftConfirmState state;
        public final ECOCraftConfirmReport report;

        Built(ECOCraftConfirmState state, ECOCraftConfirmReport report) {
            this.state = state;
            this.report = report;
        }
    }

    /** The report shown while ECO is still planning. */
    public static ECOCraftConfirmState calculating(boolean cyclePlanningEnabled) {
        return new ECOCraftConfirmState(
            true,
            false,
            cyclePlanningEnabled,
            false,
            0L,
            BigInteger.ZERO,
            null,
            "",
            0,
            true,
            0L,
            0L,
            null,
            "");
    }

    /** Planning ended without an executable plan (the planner threw, or returned no plan). */
    public static Built failed(String message, boolean cyclePlanningEnabled, long version) {
        return new Built(
            new ECOCraftConfirmState(
                true,
                true,
                cyclePlanningEnabled,
                false,
                0L,
                BigInteger.ZERO,
                PlanningStatus.INTERNAL_ERROR,
                message == null ? "" : message,
                0,
                true,
                0L,
                version,
                null,
                ""),
            new ECOCraftConfirmReport(version, Collections.emptyList(), ECOCycleItemList.EMPTY));
    }

    public static Built build(ICraftingPlan plan, @Nullable GridView grid, @Nullable IActionSource source,
        boolean cyclePlanningEnabled, long version) {
        ECOPlanningResult result = resolve(plan);
        CraftingGraphSnapshot snapshot = CraftingGraphSnapshot.EMPTY;
        ECOCycleItemList cycles = ECOCycleItemList.EMPTY;
        String diagnostic = "";
        if (result != null) {
            try {
                snapshot = CraftingGraphSnapshotFactory.create(result);
            } catch (RuntimeException failure) {
                LOGGER.warn("[craft-confirm] ECO report snapshot failed for {}", plan.finalOutput(), failure);
            }
            try {
                cycles = cycleItems(result, snapshot);
            } catch (RuntimeException failure) {
                LOGGER.warn("[craft-confirm] ECO cycle list failed for {}", plan.finalOutput(), failure);
            }
            diagnostic = diagnostic(result);
            if (!diagnostic.isEmpty())
                // Keep the complete failure evidence in the server log; only the UI payload is bounded.
                LOGGER.warn(
                    "[craft-confirm] ECO diagnostic: status={}, output={}, diagnostics={}",
                    result.status(),
                    plan.finalOutput(),
                    diagnostic);
        }
        PlanningStatus status = result == null ? null : result.status();
        List<CraftingPlanSummaryEntry> summary = summary(plan, grid, source);
        boolean exact = ECOCraftConfirmRules.useExactMaterialTable(
            status,
            plan.simulation(),
            summary.isEmpty(),
            !snapshot.nodes()
                .isEmpty(),
            !cycles.items()
                .isEmpty());
        List<ECOCraftConfirmReport.Row> rows;
        if (exact) {
            boolean shell = ECOCraftConfirmRules.isDiagnosticShell(status, plan.simulation(), summary.isEmpty());
            rows = nodeRows(shell ? snapshot.nodes() : sortMaterials(snapshot.nodes()));
        } else {
            rows = new ArrayList<>(summary.size());
            for (CraftingPlanSummaryEntry entry : summary) rows.add(
                new ECOCraftConfirmReport.Row(
                    entry.getWhat(),
                    entry.getStoredAmount(),
                    entry.getMissingAmount(),
                    entry.getCraftAmount()));
        }
        rows = withRoundsAndStock(rows, plan, grid);
        BigInteger theoretical = result == null ? BigInteger.ZERO : result.theoreticalBytes();
        ECOCraftConfirmState state = new ECOCraftConfirmState(
            true,
            true,
            cyclePlanningEnabled,
            !snapshot.cycleGroups()
                .isEmpty(),
            result == null ? 0L : result.calculationNanos(),
            theoretical,
            status,
            diagnostic,
            0,
            plan.simulation(),
            plan.bytes(),
            version,
            null,
            "");
        return new Built(state, new ECOCraftConfirmReport(version, rows, cycles));
    }

    /** Exact-object provenance first, then the strict full-plan identity (1.21's menu-boundary recovery). */
    public static @Nullable ECOPlanningResult resolve(@Nullable ICraftingPlan plan) {
        if (plan == null) return null;
        ECOPlanningResult result = plan instanceof ECOCraftingPlanDiagnostics
            ? ((ECOCraftingPlanDiagnostics) plan).neoecoae$getPlanningResult()
            : null;
        if (result == null) result = ECOPlanningResultRegistry.findExact(plan);
        if (result == null) result = ECOPlanningResultRegistry.find(plan);
        return result;
    }

    private static String diagnostic(ECOPlanningResult result) {
        if (!result.shouldUseNativeFallback()) return "";
        StringBuilder text = new StringBuilder();
        for (var diagnostic : result.trace()
            .diagnostics()) {
            if (text.length() > 0) text.append('\n');
            text.append(diagnostic.code())
                .append(": ")
                .append(diagnostic.message());
        }
        return text.toString();
    }

    private static List<CraftingPlanSummaryEntry> summary(ICraftingPlan plan, @Nullable GridView grid,
        @Nullable IActionSource source) {
        if (grid != null && source != null) {
            try {
                return CraftingPlanSummary.fromJob(grid, source, plan)
                    .getEntries();
            } catch (RuntimeException failure) {
                LOGGER.warn("[craft-confirm] ECO summary failed for {}", plan.finalOutput(), failure);
            }
        }
        // Without a live grid, describe the plan itself: used and emitted as stored, missing as missing.
        Map<AEKey, long[]> amounts = new LinkedHashMap<>();
        for (var entry : plan.usedItems())
            amounts.computeIfAbsent(entry.getKey(), key -> new long[3])[0] += entry.getLongValue();
        for (var entry : plan.missingItems())
            amounts.computeIfAbsent(entry.getKey(), key -> new long[3])[1] += entry.getLongValue();
        for (var task : plan.patternTimes()
            .entrySet())
            for (var output : task.getKey()
                .getOutputs())
                amounts.computeIfAbsent(
                    output.what(),
                    key -> new long[3])[2] += cn.dancingsnow.neoecoae.crafting.amount.NEMath
                        .saturatingMultiply(output.amount(), task.getValue());
        List<CraftingPlanSummaryEntry> entries = new ArrayList<>(amounts.size());
        for (var entry : amounts.entrySet()) entries.add(
            new CraftingPlanSummaryEntry(
                entry.getKey(),
                entry.getValue()[1],
                entry.getValue()[0],
                entry.getValue()[2]));
        Collections.sort(entries);
        return entries;
    }

    /** Adds GTNH's per-item execution count and the network stock behind its usage percentage. */
    private static List<ECOCraftConfirmReport.Row> withRoundsAndStock(List<ECOCraftConfirmReport.Row> rows,
        ICraftingPlan plan, @Nullable GridView grid) {
        Map<AEKey, BigInteger> rounds = new HashMap<>();
        try {
            for (var task : plan.patternTimes()
                .entrySet()) {
                BigInteger times = BigInteger.valueOf(task.getValue());
                Set<AEKey> outputs = new LinkedHashSet<>();
                for (var output : task.getKey()
                    .getOutputs()) outputs.add(output.what());
                for (AEKey output : outputs) rounds.merge(output, times, BigInteger::add);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("[craft-confirm] ECO round counts failed for {}", plan.finalOutput(), failure);
        }
        KeyCounter stock = null;
        if (grid != null) {
            try {
                stock = grid.getStorageService()
                    .getInventory()
                    .getAvailableStacks();
            } catch (RuntimeException failure) {
                LOGGER.warn("[craft-confirm] ECO stock lookup failed for {}", plan.finalOutput(), failure);
            }
        }
        List<ECOCraftConfirmReport.Row> result = new ArrayList<>(rows.size());
        for (ECOCraftConfirmReport.Row row : rows) {
            BigInteger count = rounds.getOrDefault(row.key, BigInteger.ZERO);
            BigInteger available = stock == null ? BigInteger.ZERO
                : BigInteger.valueOf(Math.max(0L, stock.get(row.key)));
            result.add(row.with(count, available));
        }
        return result;
    }

    private static List<ECOCraftConfirmReport.Row> nodeRows(List<CraftingGraphSnapshot.MaterialNode> nodes) {
        List<ECOCraftConfirmReport.Row> rows = new ArrayList<>(nodes.size());
        for (var node : nodes) rows.add(
            new ECOCraftConfirmReport.Row(
                node.key(),
                node.fromInventoryBigInteger(),
                node.missingBigInteger(),
                node.toCraftBigInteger(),
                node.requestedBigInteger()));
        return rows;
    }

    /** Most-missing first, then most crafted and most stored; rows without any amount are hidden. */
    static List<CraftingGraphSnapshot.MaterialNode> sortMaterials(List<CraftingGraphSnapshot.MaterialNode> nodes) {
        List<CraftingGraphSnapshot.MaterialNode> sorted = new ArrayList<>();
        for (var node : nodes) if (hasAmount(node)) sorted.add(node);
        sorted.sort(
            Comparator.comparing(CraftingGraphSnapshot.MaterialNode::missingBigInteger)
                .reversed()
                .thenComparing(CraftingGraphSnapshot.MaterialNode::toCraftBigInteger, Comparator.reverseOrder())
                .thenComparing(CraftingGraphSnapshot.MaterialNode::fromInventoryBigInteger, Comparator.reverseOrder())
                .thenComparing(
                    node -> node.key()
                        .toString()));
        return sorted;
    }

    private static boolean hasAmount(CraftingGraphSnapshot.MaterialNode node) {
        return node.requestedBigInteger()
            .signum() > 0
            || node.fromInventoryBigInteger()
                .signum() > 0
            || node.toCraftBigInteger()
                .signum() > 0
            || node.missingBigInteger()
                .signum() > 0;
    }

    /** Port of 1.21's cycle member list: every SCC member with a known total or a concrete startup shortage. */
    static ECOCycleItemList cycleItems(ECOPlanningResult result, CraftingGraphSnapshot snapshot) {
        LinkedHashMap<AEKey, ECOCycleItemList.Entry> items = new LinkedHashMap<>();
        for (var cycle : snapshot.cycleGroups()) {
            Map<AEKey, Long> seedParallelism = Collections.emptyMap();
            for (ComponentPlanningResult component : result.components()) {
                if (component.componentId() == cycle.componentId() && component.cycleResult() != null
                    && component.status() == ComponentPlanningResult.Status.PLANNED) {
                    seedParallelism = component.cycleResult()
                        .seedParallelism(
                            component.cycleResult()
                                .requiredSeed());
                    break;
                }
            }
            Set<AEKey> keys = new LinkedHashSet<>();
            for (var value : cycle.exactSingleNetOutputs()) keys.add(value.key());
            for (var value : cycle.exactTotalNetOutputs()) keys.add(value.key());
            for (var value : cycle.availableAmounts()) keys.add(value.key());
            for (var value : cycle.externalInputs()) keys.add(value.key());
            for (var value : cycle.requiredOutputs()) keys.add(value.key());
            for (int memberId : cycle.memberNodeIds()) {
                var node = nodeById(snapshot, memberId);
                if (node != null) keys.add(node.key());
            }
            for (var pattern : snapshot.patterns()) {
                if (pattern.componentId() != cycle.componentId() || pattern.firingCount() <= 0L) continue;
                for (var relation : pattern.inputs()) {
                    var node = nodeById(snapshot, relation.materialNodeId());
                    if (node != null) keys.add(node.key());
                }
                for (var relation : pattern.outputs()) {
                    var node = nodeById(snapshot, relation.materialNodeId());
                    if (node != null) keys.add(node.key());
                }
            }
            // A partially populated diagnostic may lack net outputs; keep every unresolved SCC selectable.
            for (ComponentPlanningResult component : result.components())
                if (component.componentId() == cycle.componentId()) keys.addAll(
                    component.externalMissingItems()
                        .keySet());
            for (AEKey key : keys) {
                var material = materialFor(snapshot, key);
                Long parallelism = seedParallelism.get(key);
                var entry = new ECOCycleItemList.Entry(
                    key,
                    material == null ? BigInteger.ZERO : material.consumedBigInteger(),
                    material == null ? BigInteger.ZERO : material.producedBigInteger(),
                    exactAmountFor(cycle.exactSingleNetOutputs(), key),
                    exactAmountFor(cycle.exactTotalNetOutputs(), key),
                    material == null ? BigInteger.ZERO : material.missingBigInteger(),
                    cycle.executionCountKnowledge(),
                    cycle.solveStatus(),
                    cycle.componentId(),
                    parallelism == null ? -1L : parallelism);
                // An unresolved cycle has no authoritative total; only concrete shortages stay actionable.
                if (entry.totalNetOutputKnown() || entry.exactMissing()
                    .signum() > 0) items.putIfAbsent(key, entry);
            }
            // Required startup seeds keep an unresolved cycle actionable even before it produces anything.
            for (var seed : cycle.requiredSeed()) {
                var material = materialFor(snapshot, seed.key());
                Long parallelism = seedParallelism.get(seed.key());
                var entry = new ECOCycleItemList.Entry(
                    seed.key(),
                    material == null ? BigInteger.ZERO : material.consumedBigInteger(),
                    material == null ? BigInteger.ZERO : material.producedBigInteger(),
                    BigInteger.ZERO,
                    BigInteger.ZERO,
                    material == null ? BigInteger.ZERO : material.missingBigInteger(),
                    cycle.executionCountKnowledge(),
                    cycle.solveStatus(),
                    cycle.componentId(),
                    parallelism == null ? -1L : parallelism);
                if (entry.totalNetOutputKnown() || entry.exactMissing()
                    .signum() > 0) items.putIfAbsent(seed.key(), entry);
            }
        }
        return items.isEmpty() ? ECOCycleItemList.EMPTY : new ECOCycleItemList(new ArrayList<>(items.values()));
    }

    private static @Nullable CraftingGraphSnapshot.MaterialNode nodeById(CraftingGraphSnapshot snapshot, int id) {
        for (var node : snapshot.nodes()) if (node.nodeId() == id) return node;
        return null;
    }

    private static @Nullable CraftingGraphSnapshot.MaterialNode materialFor(CraftingGraphSnapshot snapshot, AEKey key) {
        for (var node : snapshot.nodes()) if (node.key()
            .equals(key)) return node;
        return null;
    }

    private static BigInteger exactAmountFor(List<ExactKeyAmount> values, AEKey key) {
        for (var value : values) if (value.key()
            .equals(key))
            return value.amount()
                .value();
        return BigInteger.ZERO;
    }
}
