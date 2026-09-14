package cn.dancingsnow.neoecoae.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;

class ECOExecutionRuntimeTest {

    private static ICraftingPatternDetails pattern(String id) {
        ItemStack stack = new ItemStack(new Item());
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("Recipe", id);
        return (ICraftingPatternDetails) Proxy.newProxyInstance(
            ICraftingPatternDetails.class.getClassLoader(),
            new Class<?>[] { ICraftingPatternDetails.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getPattern")) return stack.copy();
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static NBTTagCompound schedule(ICraftingPatternDetails first, long amount, ICraftingPatternDetails second) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Version", 1);
        NBTTagList steps = new NBTTagList();
        for (ICraftingPatternDetails pattern : new ICraftingPatternDetails[] { first, second }) {
            NBTTagCompound step = new NBTTagCompound();
            step.setTag(
                "Pattern",
                pattern.getPattern()
                    .writeToNBT(new NBTTagCompound()));
            step.setLong("Remaining", pattern == first ? amount : 1);
            steps.appendTag(step);
        }
        data.setTag("Steps", steps);
        return data;
    }

    @Test
    void partialBatchAndRestartPreserveOrderAndCounts() {
        ICraftingPatternDetails first = pattern("first");
        ICraftingPatternDetails second = pattern("second");
        ECOExecutionRuntime runtime = ECOExecutionRuntime.read(schedule(first, 64, second));
        assertEquals(0, runtime.allowance(second));
        runtime.dispatched(first, 16);
        runtime = ECOExecutionRuntime.read(runtime.write());
        assertEquals(48, runtime.allowance(first));
        assertEquals(0, runtime.allowance(second));
        runtime.dispatched(first, 48);
        assertEquals(1, runtime.allowance(second));
        runtime.dispatched(second, 1);
        assertTrue(runtime.finishedDispatching());
        assertTrue(
            ECOExecutionRuntime.read(runtime.write())
                .finishedDispatching());
    }

    @Test
    void rejectsOverdispatchWrongPatternAndCorruptState() {
        ICraftingPatternDetails first = pattern("first");
        ICraftingPatternDetails second = pattern("second");
        ECOExecutionRuntime runtime = ECOExecutionRuntime.read(schedule(first, 2, second));
        assertThrows(IllegalStateException.class, () -> runtime.dispatched(first, 3));
        assertThrows(IllegalStateException.class, () -> runtime.dispatched(second, 1));
        assertEquals(2, runtime.allowance(first));
        assertThrows(IllegalArgumentException.class, () -> ECOExecutionRuntime.read(schedule(first, -1, second)));
        assertThrows(IllegalArgumentException.class, () -> ECOExecutionRuntime.read(new NBTTagCompound()));
    }
}
