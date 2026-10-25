package cn.dancingsnow.neoecoae.platform;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;

/** Serializable fixture for an addon channel, with no claimed native channel implementation. */
public final class TestAddonKey extends AEKey {

    private static final AEKeyType TYPE = AEKeyType.register(ResourceLocation.parse("neoecoae_test:addon"), null, 8, 1);
    public static final TestAddonKey INSTANCE = new TestAddonKey();
    static {
        AEKey.registerDecoder(
            TYPE.getId()
                .toString(),
            tag -> INSTANCE);
    }

    private TestAddonKey() {}

    @Override
    public AEKeyType getType() {
        return TYPE;
    }

    @Override
    public Object getPrimaryKey() {
        return INSTANCE;
    }

    @Override
    public String getDisplayName() {
        return "addon fixture";
    }

    @Override
    public appeng.api.storage.data.IAEStack<?> toNative(long amount) {
        throw new UnsupportedOperationException("Test addon has no native inventory");
    }

    @Override
    public CompoundTag toTagGeneric(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(
            "#type",
            TYPE.getId()
                .toString());
        return tag;
    }
}
