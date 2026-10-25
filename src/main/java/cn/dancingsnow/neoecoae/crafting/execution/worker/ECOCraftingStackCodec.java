package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEFluidKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.GenericStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/** Converts crafting recovery counters to the persistable generic-stack representation. */
public final class ECOCraftingStackCodec {

    private ECOCraftingStackCodec() {}

    public static KeyCounter collect(List<ItemStack> stacks) {
        KeyCounter result = new KeyCounter();
        for (ItemStack stack : stacks) {
            if (!cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack))
                result.add(AEItemKey.of(stack), cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.count(stack));
        }
        return result;
    }

    public static List<GenericStack> toGenericStacks(KeyCounter counter, boolean allowFluid) {
        List<GenericStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() <= 0) continue;
            if (!(entry.getKey() instanceof AEItemKey) && !(allowFluid && entry.getKey() instanceof AEFluidKey))
                return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.listOf();
            stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return stacks;
    }

    public static boolean canRetain(List<GenericStack> stacks, boolean allowFluid) {
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0
                || (!(stack.what() instanceof AEItemKey) && !(allowFluid && stack.what() instanceof AEFluidKey)))
                return false;
        }
        return true;
    }

    public static boolean isEmpty(KeyCounter counter) {
        return !counter.iterator()
            .hasNext();
    }
}
