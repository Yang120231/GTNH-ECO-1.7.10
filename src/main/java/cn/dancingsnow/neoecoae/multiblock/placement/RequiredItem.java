package cn.dancingsnow.neoecoae.multiblock.placement;

import net.minecraft.item.ItemStack;

@com.github.bsideup.jabel.Desugar
public record RequiredItem(ItemStack stack, int count) {

    public RequiredItem {
        stack = cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(stack, 1);
        count = Math.max(0, count);
    }

    @Override
    public ItemStack stack() {
        return cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copy(stack);
    }

    public ItemStack stackWithCount() {
        return cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.copyWithCount(stack, count);
    }

    public boolean isEmpty() {
        return cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack) || count <= 0;
    }

    public RequiredItem grow(int amount) {
        return new RequiredItem(stack, count + amount);
    }
}
