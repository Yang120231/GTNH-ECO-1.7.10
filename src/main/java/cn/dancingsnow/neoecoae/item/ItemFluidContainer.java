package cn.dancingsnow.neoecoae.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

public class ItemFluidContainer extends Item {

    private final String fluidName;

    public ItemFluidContainer(String id, String fluidName, int capacity, CreativeTabs creativeTab) {
        this.fluidName = fluidName;
        this.setUnlocalizedName(id);
        this.setCreativeTab(creativeTab);
        this.setMaxStackSize(1);
        this.setContainerItem(Items.bucket);
    }

    @Override
    public boolean doesContainerItemLeaveCraftingGrid(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack getContainerItem(ItemStack itemStack) {
        return new ItemStack(Items.bucket);
    }

    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return true;
    }

    public Fluid getFluid() {
        return FluidRegistry.getFluid(this.fluidName);
    }
}
