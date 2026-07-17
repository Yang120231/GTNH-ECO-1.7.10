package cn.dancingsnow.neoecoae.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

class TileCraftingWorkerPersistenceTest {

    @Test
    void largeBatchUsesOneCountedNbtStackWithoutAmountTruncation() {
        ItemStack input = new ItemStack(new Item(), 64);
        List<ItemStack> multiplied = TileCraftingWorker.multiplyStack(input, 5632);

        assertEquals(1, multiplied.size());
        assertEquals(360448, multiplied.get(0).stackSize);

        NBTTagList encoded = TileCraftingWorker.writeStacks(multiplied);
        assertEquals(1, encoded.tagCount());
        assertEquals(
            360448,
            encoded.getCompoundTagAt(0)
                .getInteger("EcoAmount"));
        assertEquals(360448, TileCraftingWorker.persistedStackAmount(encoded.getCompoundTagAt(0), 1));
    }
}
