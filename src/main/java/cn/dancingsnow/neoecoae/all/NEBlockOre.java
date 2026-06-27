package cn.dancingsnow.neoecoae.all;

import java.util.Random;

import net.minecraft.block.BlockOre;
import net.minecraft.item.Item;

public class NEBlockOre extends BlockOre {

    private final Item droppedItem;

    public NEBlockOre(Item droppedItem) {
        this.droppedItem = droppedItem;
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return this.droppedItem;
    }
}
