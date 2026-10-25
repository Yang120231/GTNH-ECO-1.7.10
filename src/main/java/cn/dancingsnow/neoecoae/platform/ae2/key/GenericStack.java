package cn.dancingsnow.neoecoae.platform.ae2.key;

@com.github.bsideup.jabel.Desugar
public record GenericStack(AEKey what, long amount) {

    public GenericStack {
        java.util.Objects.requireNonNull(what, "what");
    }

    public static GenericStack fromItemStack(net.minecraft.item.ItemStack stack) {
        if (cn.dancingsnow.neoecoae.platform.minecraft.ItemStacks.isEmpty(stack)) return null;
        if (stack.getItem()
            .getClass()
            .getName()
            .equals("com.glodblock.github.common.item.ItemFluidDrop")) {
            var nativeStack = appeng.util.Platform.convertStack(appeng.util.item.AEItemStack.create(stack));
            var key = AEKey.fromNative(nativeStack);
            return key == null ? null : new GenericStack(key, nativeStack.getStackSize());
        }
        return new GenericStack(AEItemKey.of(stack), stack.stackSize);
    }

    public static GenericStack readTag(cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider registries,
        cn.dancingsnow.neoecoae.platform.nbt.CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        var key = AEKey.fromTagGeneric(registries, tag);
        return key == null ? null : new GenericStack(key, tag.getLong("#"));
    }

    public static cn.dancingsnow.neoecoae.platform.nbt.CompoundTag writeTag(
        cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider registries, GenericStack stack) {
        if (stack == null) return new cn.dancingsnow.neoecoae.platform.nbt.CompoundTag();
        var tag = stack.what()
            .toTagGeneric(registries);
        tag.putLong("#", stack.amount());
        return tag;
    }
}
