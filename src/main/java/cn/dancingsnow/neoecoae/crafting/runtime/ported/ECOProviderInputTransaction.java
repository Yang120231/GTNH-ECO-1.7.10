package cn.dancingsnow.neoecoae.crafting.runtime.ported;

import java.util.List;

import cn.dancingsnow.neoecoae.crafting.fastpath.ported.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.crafting.fastpath.ported.ECOBatchCraftingHelper.BatchInventory;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;

/** Exact CPU-input ownership transfer shared by non-Fastpath provider batches. */
public final class ECOProviderInputTransaction {

    private final BatchInventory inventory;
    private final List<GenericStack> inputs;
    private boolean settled;

    private ECOProviderInputTransaction(BatchInventory inventory, List<GenericStack> inputs) {
        this.inventory = inventory;
        this.inputs = com.google.common.collect.ImmutableList.copyOf(inputs);
    }

    public static ECOProviderInputTransaction begin(BatchInventory inventory, List<GenericStack> inputs) {
        return ECOBatchCraftingHelper.extractExact(inventory, inputs)
            ? new ECOProviderInputTransaction(inventory, inputs)
            : null;
    }

    public void transferOwnership() {
        settled = true;
    }

    public void rollback() {
        if (!settled) {
            settled = true;
            ECOBatchCraftingHelper.insertAll(inventory, inputs);
        }
    }
}
