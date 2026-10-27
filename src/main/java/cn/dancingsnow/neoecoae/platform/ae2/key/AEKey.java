package cn.dancingsnow.neoecoae.platform.ae2.key;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Immutable resource identity. Native stacks are copied at every ingress and egress. */
public abstract class AEKey {

    public static final String TYPE_FIELD = "#type";

    private static final java.util.Map<String, java.util.function.Function<CompoundTag, AEKey>> DECODERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerDecoder(String id, java.util.function.Function<CompoundTag, AEKey> decoder) {
        cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation.parse(id);
        if (id.equals("ae2:item") || id.equals("ae2:fluid")
            || id.equals("ae2:i")
            || id.equals("ae2:f")
            || DECODERS.putIfAbsent(id, java.util.Objects.requireNonNull(decoder)) != null)
            throw new IllegalArgumentException("Duplicate key decoder: " + id);
    }

    public abstract AEKeyType getType();

    public abstract Object getPrimaryKey();

    public cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation getId() {
        return null;
    }

    public int getFuzzySearchValue() {
        return 0;
    }

    public int getFuzzySearchMaxValue() {
        return 0;
    }

    public int getAmountPerUnit() {
        return getType().getAmountPerUnit();
    }

    public int getAmountPerOperation() {
        return getType().nativeChannel() == appeng.api.storage.StorageChannel.FLUIDS ? 125 : 1;
    }

    public boolean matches(GenericStack stack) {
        return stack != null && equals(stack.what());
    }

    public int getAmountPerByte() {
        return getType().getAmountPerByte();
    }

    public abstract String getDisplayName();

    public abstract IAEStack<?> toNative(long amount);

    public abstract CompoundTag toTagGeneric(HolderLookup.Provider registries);

    public static void writeKey(cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf data, AEKey key) {
        data.writeBoolean(key != null);
        if (key != null) data.writeNbt(key.toTagGeneric(data.registryAccess()));
    }

    public static AEKey readKey(cn.dancingsnow.neoecoae.platform.network.RegistryFriendlyByteBuf data) {
        return data.readBoolean() ? fromTagGeneric(data.registryAccess(), data.readNbt()) : null;
    }

    public static AEKey fromTagGeneric(HolderLookup.Provider registries, CompoundTag tag) {
        switch (tag.getString("#type")) {
            case "ae2:item":
            case "ae2:i":
                return AEItemKey.read(registries, tag);
            case "ae2:fluid":
            case "ae2:f":
                return AEFluidKey.read(registries, tag);
            default:
                java.util.function.Function<CompoundTag, AEKey> decoder = DECODERS.get(tag.getString("#type"));
                return decoder == null ? null : decoder.apply(tag.copy());
        }
    }

    public static AEKey fromNative(IAEStack<?> stack) {
        if (stack instanceof IAEItemStack) return AEItemKey.of(((IAEItemStack) stack).getItemStack());
        if (stack instanceof IAEFluidStack) return AEFluidKey.of(((IAEFluidStack) stack).getFluidStack());
        return null;
    }

    /** Remove mutable NBT while retaining the native item variant or fluid identity for provider blocking. */
    public AEKey dropSecondary() {
        if (this instanceof AEItemKey) {
            net.minecraft.item.ItemStack stack = ((AEItemKey) this).toStack(1);
            stack.setTagCompound(null);
            return AEItemKey.of(stack);
        }
        if (this instanceof AEFluidKey) {
            net.minecraftforge.fluids.FluidStack stack = ((AEFluidKey) this).toStack(1);
            stack.tag = null;
            return AEFluidKey.of(stack);
        }
        return this;
    }
}
