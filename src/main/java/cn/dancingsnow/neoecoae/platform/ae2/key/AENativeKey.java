package cn.dancingsnow.neoecoae.platform.ae2.key;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Identity for native AE2 resource types such as essentia, using their registered stack codec. */
public final class AENativeKey extends AEKey {

    private final IAEStack<?> identity;
    private final AEKeyType type;
    private final int hash;

    private AENativeKey(IAEStack<?> stack) {
        identity = stack.copy();
        identity.reset();
        identity.setStackSize(1);
        type = AEKeyType.fromNative(identity.getStackType());
        hash = 31 * type.hashCode() + identity.hashCode();
    }

    static AENativeKey ofNative(IAEStack<?> stack) {
        if (stack == null || stack.getStackType() == null
            || AEStackTypeRegistry.getType(
                stack.getStackType()
                    .getId())
                != stack.getStackType())
            return null;
        return new AENativeKey(stack);
    }

    @Override
    public AEKeyType getType() {
        return type;
    }

    @Override
    public Object getPrimaryKey() {
        // The native API exposes precise identity, but no immutable primary/secondary split.
        return this;
    }

    @Override
    public String getDisplayName() {
        return toNative(1).getDisplayName();
    }

    @Override
    public IAEStack<?> toNative(long amount) {
        return identity.copy()
            .setStackSize(amount);
    }

    @Override
    public CompoundTag toTagGeneric(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(
            TYPE_FIELD,
            type.getId()
                .toString());
        tag.put("stack", new CompoundTag(toNative(1).toNBTGeneric()));
        return tag;
    }

    static AEKey read(IAEStackType<?> type, CompoundTag tag) {
        IAEStack<?> stack = IAEStack.fromNBTGeneric(
            tag.getCompound("stack")
                .copy()
                .nativeTag());
        return stack != null && stack.getStackType() == type ? AEKey.fromNative(stack) : null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof AENativeKey key && type == key.type && identity.equals(key.identity);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return toNative(1).toNBTGeneric()
            .toString();
    }
}
