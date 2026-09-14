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
import cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOCraftingBatchTransaction;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionHost;
import cn.dancingsnow.neoecoae.crafting.runtime.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.mixin.MixinCraftingTaskProgress;
import cn.dancingsnow.neoecoae.tile.TileECOController;

/** Extends the verified provider path to ordinary GTNH AE CPUs, without replacing AE's dispatch loop. */
public final class ECOExternalCpuBatch {

    public interface Accounting {

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
        double availableEnergy = power
            .extractAEPower(perCraft * requested, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        requested = (int) Math.min(requested, Math.floor(availableEnergy / perCraft));
        if (requested < 2) return null;
        final int crafts = requested;
        final double extraEnergy = perCraft * (crafts - 1);
        // Validate all multiplication/accounting BEFORE reserving anything.
        Map<ECOResourceKey, Long> outputs = ECOCraftingSnapshot.amounts(pattern.getCondensedAEOutputs());
        for (long output : outputs.values()) Math.multiplyExact(output, crafts);
        List<IAEStack<?>> reserved = new ArrayList<>();
        try {
            for (Map.Entry<ECOResourceKey, Long> input : inputs.entrySet()) {
                IAEStack request = input.getKey()
                    .stack(Math.multiplyExact(input.getValue(), crafts - 1L));
                IAEStack taken = cpu.getInventory()
                    .extractItems(request, Actionable.MODULATE);
                if (taken != null) reserved.add(taken);
                if (taken == null || taken.getStackSize() != request.getStackSize()) {
                    restore(cpu, reserved);
                    return null;
                }
            }
        } catch (RuntimeException failure) {
            restore(cpu, reserved);
            throw failure;
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
                double charged = 0;
                try {
                    charged = power.extractAEPower(extraEnergy, Actionable.MODULATE, PowerMultiplier.CONFIG);
                } catch (RuntimeException failure) {
                    // The provider already accepted the work. Account it once and retain the unpaid energy.
                    cn.dancingsnow.neoecoae.NeoECOAE.LOG.error("Accepted external batch has unpaid energy", failure);
                } finally {
                    double debt = Double.isFinite(charged) ? Math.max(0, extraEnergy - charged) : extraEnergy;
                    accounting.accepted(pattern, crafts - 1, debt);
                }
            }

            @Override
            public void rollback() {
                if (finished) return;
                finished = true;
                progress.neoecoae$setValue(remaining);
                restore(cpu, reserved);
            }
        };
    }

    private static void restore(CraftingCPUCluster cpu, List<IAEStack<?>> reserved) {
        for (IAEStack<?> stack : reserved) cpu.getInventory()
            .injectItems(stack, Actionable.MODULATE);
    }
}
