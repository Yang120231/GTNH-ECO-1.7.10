package cn.dancingsnow.neoecoae.platform.ae2.key;

import java.util.Objects;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

public final class AEFluidKey extends AEKey {

    private final Fluid fluid;
    private final net.minecraft.nbt.NBTTagCompound data;
    private final int hash;

    private AEFluidKey(FluidStack stack) {
        this(stack.getFluid(), stack.tag);
    }

    private AEFluidKey(Fluid fluid, net.minecraft.nbt.NBTTagCompound data) {
        this.fluid = Objects.requireNonNull(fluid);
        this.data = data == null ? null : (net.minecraft.nbt.NBTTagCompound) data.copy();
        hash = Objects.hash(fluid, this.data);
    }

    public static AEFluidKey of(FluidStack stack) {
        return stack == null || stack.getFluid() == null ? null : new AEFluidKey(stack);
    }

    public static AEFluidKey of(Fluid fluid) {
        return fluid == null ? null : new AEFluidKey(fluid, null);
    }

    public static AEFluidKey of(cn.dancingsnow.neoecoae.platform.fluid.FluidStack stack) {
        return stack == null || stack.isEmpty() ? null : new AEFluidKey(stack.getFluid(), stack.copyTag());
    }

    public Fluid getFluid() {
        return fluid;
    }

    @Override
    public cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation getId() {
        String name = fluid.getName();
        return name.indexOf(':') >= 0 ? cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse(name)
            : cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.fromNamespaceAndPath("forge", name);
    }

    public FluidStack toStack(int amount) {
        FluidStack stack = new FluidStack(fluid, amount);
        stack.tag = data == null ? null : (net.minecraft.nbt.NBTTagCompound) data.copy();
        return stack;
    }

    @Override
    public AEKeyType getType() {
        return AEKeyType.fluids();
    }

    @Override
    public Object getPrimaryKey() {
        return getFluid();
    }

    @Override
    public String getDisplayName() {
        return fluid.getLocalizedName(toStack(1));
    }

    @Override
    public IAEFluidStack toNative(long amount) {
        IAEFluidStack stack = AEFluidStack.create(toStack(1));
        return stack == null ? null : stack.setStackSize(amount);
    }

    @Override
    public CompoundTag toTagGeneric(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("FluidName", fluid.getName());
        tag.putInt("Amount", 1);
        if (data != null) tag.put("Tag", new CompoundTag((net.minecraft.nbt.NBTTagCompound) data.copy()));
        tag.putString(
            "#type",
            getType().getId()
                .toString());
        return tag;
    }

    static AEFluidKey read(HolderLookup.Provider registries, CompoundTag tag) {
        Fluid fluid = registries.fluid(tag.getString("FluidName"));
        return fluid == null ? null
            : new AEFluidKey(
                fluid,
                tag.contains("Tag", 10) ? tag.getCompound("Tag")
                    .nativeTag() : null);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AEFluidKey && fluid == ((AEFluidKey) other).fluid
            && Objects.equals(data, ((AEFluidKey) other).data);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return getFluid().getName() + ":" + String.valueOf(data);
    }
}
