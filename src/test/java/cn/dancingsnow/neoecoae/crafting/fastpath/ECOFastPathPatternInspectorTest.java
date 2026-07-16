package cn.dancingsnow.neoecoae.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class ECOFastPathPatternInspectorTest {

    @Test
    void onlyContextIndependentStacksUseBatchPath() {
        assertTrue(ECOFastPathPatternInspector.isFastPathSafe(new ItemStack(new Item()), true));

        ItemStack tagged = new ItemStack(new Item());
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound().setBoolean("dynamic", true);
        assertFalse(ECOFastPathPatternInspector.isFastPathSafe(tagged, true));

        ItemStack damageable = new ItemStack(new Item().setMaxDamage(100));
        assertFalse(ECOFastPathPatternInspector.isFastPathSafe(damageable, true));

        Item container = new Item();
        ItemStack returnedContainer = new ItemStack(new Item().setContainerItem(container));
        assertFalse(ECOFastPathPatternInspector.isFastPathSafe(returnedContainer, true));
        assertTrue(ECOFastPathPatternInspector.isFastPathSafe(returnedContainer, false));
    }
}
