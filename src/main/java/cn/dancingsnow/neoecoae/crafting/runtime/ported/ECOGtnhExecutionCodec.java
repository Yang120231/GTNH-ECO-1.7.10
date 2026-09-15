package cn.dancingsnow.neoecoae.crafting.runtime.ported;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;
import cn.dancingsnow.neoecoae.crafting.planner.ECOResourceKey;
import cn.dancingsnow.neoecoae.crafting.planner.ported.compile.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ported.identity.PlanIdentity;

/** GTNH registry boundary; preserves native stack types and long counts. */
public final class ECOGtnhExecutionCodec implements ECOExecutionPlanCodec.Codec {

    private final World world;

    public ECOGtnhExecutionCodec(World world) {
        this.world = world;
    }

    public NBTTagCompound write(Object key, long amount) {
        NBTTagCompound data = new NBTTagCompound();
        Platform.writeStackNBT(((ECOResourceKey) key).stack(amount), data, true);
        return data;
    }

    public GenericStack read(NBTTagCompound data) {
        IAEStack<?> stack = Platform.readStackNBT(data);
        return stack == null ? null : new GenericStack(new ECOResourceKey(stack), stack.getStackSize());
    }

    public NBTTagCompound writePattern(ICraftingPatternDetails pattern) {
        ItemStack encoded = pattern.getPattern();
        if (encoded == null) throw new IllegalArgumentException("Missing encoded pattern");
        encoded = encoded.copy();
        encoded.stackSize = 1;
        return encoded.writeToNBT(new NBTTagCompound());
    }

    public ICraftingPatternDetails readPattern(NBTTagCompound data) {
        ItemStack encoded = ItemStack.loadItemStackFromNBT(data);
        if (encoded == null || !(encoded.getItem() instanceof ICraftingPatternItem)) return null;
        return ((ICraftingPatternItem) encoded.getItem()).getPatternForItem(encoded, world);
    }

    public PlanIdentity.PatternIdentity identity(ICraftingPatternDetails pattern) {
        return new PlanIdentity.PatternIdentity(PlanIdentity.Kind.DEFINITION, writePattern(pattern));
    }
}
