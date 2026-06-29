package cn.dancingsnow.neoecoae.storage.core;

import net.minecraft.nbt.NBTTagCompound;

public final class ECOStorageKey {

    public static final String CHANNEL_ITEM = "item";
    public static final String CHANNEL_FLUID = "fluid";
    public static final String CHANNEL_CUSTOM = "custom";
    public static final int WILDCARD_METADATA = -1;

    private final String channel;
    private final String identity;
    private final int metadata;
    private final String variant;

    private ECOStorageKey(String channel, String identity, int metadata, String variant) {
        this.channel = requireText(channel, "channel");
        this.identity = requireText(identity, "identity");
        this.metadata = metadata;
        this.variant = variant == null ? "" : variant;
    }

    public static ECOStorageKey item(String itemName, int damage, String tagFingerprint) {
        return new ECOStorageKey(CHANNEL_ITEM, itemName, damage, tagFingerprint);
    }

    public static ECOStorageKey fluid(String fluidName, String tagFingerprint) {
        return new ECOStorageKey(CHANNEL_FLUID, fluidName, WILDCARD_METADATA, tagFingerprint);
    }

    public static ECOStorageKey custom(String identity, String variant) {
        return custom(CHANNEL_CUSTOM, identity, WILDCARD_METADATA, variant);
    }

    public static ECOStorageKey custom(String channel, String identity, int metadata, String variant) {
        return new ECOStorageKey(channel, identity, metadata, variant);
    }

    public static ECOStorageKey readFromNBT(NBTTagCompound tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Storage key tag must not be null");
        }
        return new ECOStorageKey(tag.getString("channel"), tag.getString("identity"), tag.getInteger("metadata"),
            tag.getString("variant"));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("channel", this.channel);
        tag.setString("identity", this.identity);
        tag.setInteger("metadata", this.metadata);
        if (this.variant.length() > 0) {
            tag.setString("variant", this.variant);
        }
        return tag;
    }

    public String getChannel() {
        return this.channel;
    }

    public String getIdentity() {
        return this.identity;
    }

    public int getMetadata() {
        return this.metadata;
    }

    public String getVariant() {
        return this.variant;
    }

    public boolean isItem() {
        return CHANNEL_ITEM.equals(this.channel);
    }

    public boolean isFluid() {
        return CHANNEL_FLUID.equals(this.channel);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException("Storage key " + name + " must not be empty");
        }
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ECOStorageKey)) {
            return false;
        }
        ECOStorageKey other = (ECOStorageKey) obj;
        return this.metadata == other.metadata && this.channel.equals(other.channel) && this.identity.equals(other.identity)
            && this.variant.equals(other.variant);
    }

    @Override
    public int hashCode() {
        int result = this.channel.hashCode();
        result = 31 * result + this.identity.hashCode();
        result = 31 * result + this.metadata;
        result = 31 * result + this.variant.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return this.channel + ":" + this.identity + ":" + this.metadata + ":" + this.variant;
    }
}
