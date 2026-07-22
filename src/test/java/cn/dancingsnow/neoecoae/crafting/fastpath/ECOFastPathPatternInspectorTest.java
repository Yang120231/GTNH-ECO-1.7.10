package cn.dancingsnow.neoecoae.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import cn.dancingsnow.neoecoae.Config;

class ECOFastPathPatternInspectorTest {

    @Test
    void onlyContextIndependentStacksUseBatchPath() {
        assertTrue(ECOFastPathPatternInspector.isFastPathSafe(new ItemStack(new Item()), true));

        ItemStack tagged = new ItemStack(new Item());
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound()
            .setBoolean("dynamic", true);
        assertFalse(ECOFastPathPatternInspector.isFastPathSafe(tagged, true));

        ItemStack damageable = new ItemStack(new Item().setMaxDamage(100));
        assertFalse(ECOFastPathPatternInspector.isFastPathSafe(damageable, true));

        Item container = new Item();
        ItemStack returnedContainer = new ItemStack(new Item().setContainerItem(container));
        assertFalse(ECOFastPathPatternInspector.isFastPathSafe(returnedContainer, true));
        assertTrue(ECOFastPathPatternInspector.isFastPathSafe(returnedContainer, false));
    }

    @Test
    void deterministicProcessingPatternCanUseTheVerifiedBatchPath() {
        boolean oldFastPath = Config.enableEcoCraftingFastPath;
        boolean oldProcessingPath = Config.enableEcoProcessingPatternFastPath;
        try {
            Config.enableEcoCraftingFastPath = true;
            Config.enableEcoProcessingPatternFastPath = true;
            assertTrue(ECOFastPathPatternInspector.isPatternTypeAllowed(false));
            assertTrue(ECOFastPathPatternInspector.isPatternTypeAllowed(true));
        } finally {
            Config.enableEcoCraftingFastPath = oldFastPath;
            Config.enableEcoProcessingPatternFastPath = oldProcessingPath;
        }
    }

    @Test
    void processingPatternBatchPathCanBeDisabledSeparately() {
        boolean oldFastPath = Config.enableEcoCraftingFastPath;
        boolean oldProcessingPath = Config.enableEcoProcessingPatternFastPath;
        try {
            Config.enableEcoCraftingFastPath = true;
            Config.enableEcoProcessingPatternFastPath = false;
            assertFalse(ECOFastPathPatternInspector.isPatternTypeAllowed(false));
            assertTrue(ECOFastPathPatternInspector.isPatternTypeAllowed(true));
        } finally {
            Config.enableEcoCraftingFastPath = oldFastPath;
            Config.enableEcoProcessingPatternFastPath = oldProcessingPath;
        }
    }

}
