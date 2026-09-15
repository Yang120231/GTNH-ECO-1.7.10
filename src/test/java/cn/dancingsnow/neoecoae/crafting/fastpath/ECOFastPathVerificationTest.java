package cn.dancingsnow.neoecoae.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class ECOFastPathVerificationTest {

    @Test
    void collidingNbtDoesNotSharePatternVerification() {
        ItemStack stack = new ItemStack(new Item());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("recipe", "Aa");
        stack.setTagCompound(tag);
        ECOFastPathPatternKey first = ECOFastPathPatternKey.of(stack);
        tag.setString("recipe", "BB");
        ECOFastPathPatternKey second = ECOFastPathPatternKey.of(stack);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, second);
        tag.setString("recipe", "Aa");
        assertEquals(first, ECOFastPathPatternKey.of(stack));
    }

}
