package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingWork;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVirtualCraftingWork;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;

/** Input to the thread work-installation state transition. */
@com.github.bsideup.jabel.Desugar
record ECOCraftingThreadWork(List<ItemStack> itemOutputs, List<ItemStack> itemInputs, List<ItemStack> itemRemaining,
    List<GenericStack> genericOutputs, List<GenericStack> genericInputs, List<GenericStack> genericRemaining,
    @Nullable UUID craftingJobId, int finiteBatchCraftCount, long craftCount, boolean virtualBatch,
    boolean resetProgress, ItemStack craftingEventOutput) {

    static ECOCraftingThreadWork items(List<ItemStack> outputs, List<ItemStack> inputs, List<ItemStack> remaining,
        @Nullable UUID craftingJobId, int finiteBatchCraftCount) {
        ItemStack eventOutput = outputs.isEmpty() ? null
            : outputs.get(0)
                .copy();
        return new ECOCraftingThreadWork(
            outputs,
            inputs,
            remaining,
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            craftingJobId,
            finiteBatchCraftCount,
            finiteBatchCraftCount,
            false,
            false,
            eventOutput);
    }

    static ECOCraftingThreadWork batch(ECOBatchCraftingWork work) {
        return new ECOCraftingThreadWork(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            work.outputTotal(),
            work.inputTotal(),
            work.remainingTotal(),
            work.craftingJobId(),
            work.batchSize(),
            work.batchSize(),
            false,
            false,
            null);
    }

    static ECOCraftingThreadWork virtual(ECOVirtualCraftingWork work) {
        return new ECOCraftingThreadWork(
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf(),
            work.outputTotal(),
            work.inputTotal(),
            work.remainingTotal(),
            work.craftingJobId(),
            1,
            work.craftCount(),
            true,
            true,
            null);
    }
}
