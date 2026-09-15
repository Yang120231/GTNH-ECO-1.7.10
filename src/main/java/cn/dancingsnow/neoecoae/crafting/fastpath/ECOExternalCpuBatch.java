package cn.dancingsnow.neoecoae.crafting.fastpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.crafting.ae2.ECOCraftingSnapshot;
import cn.dancingsnow.neoecoae.crafting.fastpath.ported.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingBatchTransaction;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionHost;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOCraftingEnergyTransaction;
import cn.dancingsnow.neoecoae.crafting.runtime.ported.ECOProviderInputTransaction;
import cn.dancingsnow.neoecoae.mixin.MixinCraftingTaskProgress;
import cn.dancingsnow.neoecoae.tile.TileECOController;

/** Extends the verified provider path to ordinary GTNH AE CPUs, without replacing AE's dispatch loop. */
public final class ECOExternalCpuBatch {

    public interface Accounting {

        ECOCraftingEnergyTransaction energyTransactions();

        void accepted(ICraftingPatternDetails pattern, int extraCrafts, double energyDebt);
    }

    private ECOExternalCpuBatch() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ECOCraftingBatchTransaction prepare(CraftingCPUCluster cpu, MixinCraftingTaskProgress progress,
        ICraftingPatternDetails pattern, InventoryCrafting table, TileECOController controller, Accounting accounting) {
        if (progress == null || cpu.getGrid() == null || cpu.isSuspended()) return null;
        ECOFastPathPlan plan = ECOFastPathPlannerHook.tryVerifiedPlan(controller, pattern, table, cpu.getGrid());
        if (!plan.accepted()) return null;
        long remaining = progress.neoecoae$getValue();
        int requested = (int) Math.min(
            Math.min(remaining, ECOFastPathConfig.batchTickLimit()),
            Math.min(cpu.getRemainingOperations(), controller.getCraftingCurrentBatchSlots()));
        ECOExecutionRuntime execution = ((ECOExecutionHost) cpu).neoecoae$getExecution();
        if (execution != null) requested = (int) Math.min(requested, execution.allowance(pattern));
        requested = controller.getCraftingCoolantCraftLimit(requested);
        if (requested < 2) return null;
        Map<ECOResourceKey, Long> inputs = ECOCraftingSnapshot.amounts(pattern.getCondensedAEInputs());
        IEnergyGrid power = cpu.getGrid()
            .getCache(IEnergyGrid.class);
        double perCraft = 0;
        for (Map.Entry<ECOResourceKey, Long> input : inputs.entrySet()) {
            IAEStack stack = input.getKey()
                .stack(Long.MAX_VALUE);
            IAEStack available = cpu.getInventory()
                .extractItems(stack, Actionable.SIMULATE);
            long additional = available == null ? 0 : available.getStackSize() / input.getValue();
            requested = (int) Math.min(requested, Math.min(Integer.MAX_VALUE, additional) + 1L);
            perCraft += (double) input.getValue() / stack.getAmountPerUnit();
        }
        if (!Double.isFinite(perCraft) || perCraft <= 0) return null;
        requested = ECOBatchCraftingHelper.maxAffordableCrafts(
            perCraft,
            requested,
            value -> power.extractAEPower(value, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        if (requested < 2) return null;
        final int crafts = requested;
        final double extraEnergy = perCraft * (crafts - 1);
        // Validate all multiplication/accounting BEFORE reserving anything.
        Map<ECOResourceKey, Long> outputs = ECOCraftingSnapshot.amounts(pattern.getCondensedAEOutputs());
        for (long output : outputs.values()) Math.multiplyExact(output, crafts);
        ECOBatchCraftingHelper.BatchInventory inventory = new ECOBatchCraftingHelper.BatchInventory() {

            public long available(Object key) {
                return extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
            }

            public long extract(Object key, long amount, Actionable mode) {
                IAEStack taken = cpu.getInventory()
                    .extractItems((IAEStack) ((ECOResourceKey) key).stack(amount), mode);
                return taken == null ? 0L : taken.getStackSize();
            }

            public void insert(Object key, long amount, Actionable mode) {
                cpu.getInventory()
                    .injectItems(((ECOResourceKey) key).stack(amount), mode);
            }
        };
        List<GenericStack> additionalInputs = new ArrayList<>();
        inputs.forEach(
            (key, amount) -> additionalInputs.add(new GenericStack(key, Math.multiplyExact(amount, crafts - 1L))));
        ECOProviderInputTransaction inputTransaction = ECOProviderInputTransaction.begin(inventory, additionalInputs);
        if (inputTransaction == null) return null;
        ECOCraftingEnergyTransaction.Reservation energy = accounting.energyTransactions()
            .reserve(power, extraEnergy);
        if (energy == null) {
            inputTransaction.rollback();
            return null;
        }
        progress.neoecoae$setValue(remaining - crafts + 1);
        return new ECOCraftingBatchTransaction() {

            private boolean finished;

            @Override
            public int craftCount() {
                return crafts;
            }

            @Override
            public void commit() {
                if (finished) return;
                finished = true;
                inputTransaction.transferOwnership();
                energy.commit();
                accounting.accepted(pattern, crafts - 1, 0.0D);
            }

            @Override
            public void rollback() {
                if (finished) return;
                finished = true;
                progress.neoecoae$setValue(remaining);
                try {
                    inputTransaction.rollback();
                } finally {
                    energy.refund();
                }
            }
        };
    }

}
