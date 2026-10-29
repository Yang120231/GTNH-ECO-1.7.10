package cn.dancingsnow.neoecoae.platform.minecraft;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display-only fluid stand-in for recipe viewers when no GregTech display stack or filled container exists. It is an
 * {@link IFluidContainerItem}, so NEI recipe/usage lookups resolve the fluid it carries; it can neither fill nor
 * drain, and it is hidden from creative tabs and the NEI item list.
 */
public final class FluidDisplayItem extends Item implements IFluidContainerItem {

    public static final FluidDisplayItem INSTANCE = new FluidDisplayItem();
    private static final String FLUID = "Fluid";

    private FluidDisplayItem() {
        setMaxStackSize(1);
        setUnlocalizedName("neoecoae.fluid_display");
    }

    public static ItemStack of(FluidStack fluid) {
        ItemStack stack = new ItemStack(INSTANCE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(FLUID, fluid.writeToNBT(new NBTTagCompound()));
        stack.setTagCompound(tag);
        return stack;
    }

    @Override
    public FluidStack getFluid(ItemStack container) {
        return container.hasTagCompound() ? FluidStack.loadFluidStackFromNBT(
            container.getTagCompound()
                .getCompoundTag(FLUID))
            : null;
    }

    @Override
    public int getCapacity(ItemStack container) {
        FluidStack fluid = getFluid(container);
        return fluid == null ? 0 : fluid.amount;
    }

    @Override
    public int fill(ItemStack container, FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack drain(ItemStack container, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        return fluid == null ? super.getItemStackDisplayName(stack) : fluid.getLocalizedName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        FluidStack fluid = getFluid(stack);
        if (fluid != null) lines.add(fluid.amount + " mB");
    }

    /** Fluid icons live on the block atlas. */
    @Override
    @SideOnly(Side.CLIENT)
    public int getSpriteNumber() {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        IIcon icon = fluid == null ? null
            : fluid.getFluid()
                .getIcon(fluid);
        return icon != null ? icon : net.minecraft.init.Blocks.water.getIcon(0, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        FluidStack fluid = getFluid(stack);
        return fluid == null ? 0xFFFFFF
            : fluid.getFluid()
                .getColor(fluid);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister register) {}
}
