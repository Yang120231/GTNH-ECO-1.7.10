package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class ECOResourceKeyTest {

    @Test
    void itemIdentityIncludesNbtAndIsIndependentOfQuantityAndMutation() {
        Item item = new Item();
        ItemStack stack = new ItemStack(item, 1, 3);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("variant", "first");
        ECOItemKey first = ECOItemKey.of(stack);
        stack.stackSize = 16;
        assertEquals(first, ECOItemKey.of(stack));
        ECOItemKey legacy = ECOItemKey.of(stack);
        stack.getTagCompound()
            .setString("variant", "second");
        assertNotEquals(first, ECOItemKey.of(stack));
        assertNotEquals(legacy, ECOItemKey.of(stack));
    }
}
