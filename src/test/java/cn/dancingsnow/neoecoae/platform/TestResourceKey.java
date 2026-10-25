package cn.dancingsnow.neoecoae.platform;

import java.util.Objects;

import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Isolated algorithm identity. Native item/fluid conversion is verified separately in the game launcher. */
public final class TestResourceKey extends AEKey {

    static {
        AEKey.registerDecoder(
            "neoecoae:test_resource",
            tag -> new TestResourceKey(tag.getString("name"), tag.getBoolean("fluid")));
    }
    private final String name;
    private final boolean fluid;

    public TestResourceKey(String name, boolean fluid) {
        this.name = name;
        this.fluid = fluid;
    }

    @Override
    public AEKeyType getType() {
        return fluid ? AEKeyType.fluids() : AEKeyType.items();
    }

    @Override
    public Object getPrimaryKey() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public IAEStack<?> toNative(long amount) {
        throw new UnsupportedOperationException("Algorithm fixture is not a native registry item");
    }

    @Override
    public CompoundTag toTagGeneric(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("#type", "neoecoae:test_resource");
        tag.putString("name", name);
        tag.putBoolean("fluid", fluid);
        return tag;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TestResourceKey && fluid == ((TestResourceKey) other).fluid
            && name.equals(((TestResourceKey) other).name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fluid);
    }

    @Override
    public String toString() {
        return name;
    }
}
