package cn.dancingsnow.neoecoae.platform.ae2.key;

import appeng.api.storage.StorageChannel;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Business units use one item / one mB. Conversion happens at the native channel boundary. */
public final class AEKeyType {

    private static final java.util.Map<ResourceLocation, AEKeyType> TYPES = new java.util.LinkedHashMap<>();

    private static final AEKeyType ITEMS = new AEKeyType("i", StorageChannel.ITEMS, 8, 1);
    private static final AEKeyType FLUIDS = new AEKeyType("f", StorageChannel.FLUIDS, 8000, 1000);
    private final ResourceLocation id;
    private final StorageChannel channel;
    private final int amountPerByte, amountPerUnit;

    private AEKeyType(String id, StorageChannel channel, int bytes, int units) {
        this(ResourceLocation.fromNamespaceAndPath("ae2", id), channel, bytes, units);
    }

    private AEKeyType(ResourceLocation id, StorageChannel channel, int bytes, int units) {
        this.id = id;
        this.channel = channel;
        amountPerByte = bytes;
        amountPerUnit = units;
        TYPES.put(id, this);
    }

    public static synchronized AEKeyType register(ResourceLocation id, StorageChannel nativeChannel, int amountPerByte,
        int amountPerUnit) {
        if (TYPES.containsKey(id) || amountPerByte < 1 || amountPerUnit < 1)
            throw new IllegalArgumentException("Invalid/duplicate resource type: " + id);
        return new AEKeyType(id, nativeChannel, amountPerByte, amountPerUnit);
    }

    public static synchronized java.util.List<AEKeyType> registeredTypes() {
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(TYPES.values()));
    }

    public static AEKeyType items() {
        return ITEMS;
    }

    public static AEKeyType fluids() {
        return FLUIDS;
    }

    public ResourceLocation getId() {
        return id;
    }

    public cn.dancingsnow.neoecoae.platform.minecraft.Component getDescription() {
        return cn.dancingsnow.neoecoae.platform.minecraft.Component.translatable(
            channel == StorageChannel.ITEMS ? "gui.tooltips.appliedenergistics2.Items"
                : "gui.tooltips.appliedenergistics2.Fluids");
    }

    public StorageChannel nativeChannel() {
        return channel;
    }

    public int getAmountPerByte() {
        return amountPerByte;
    }

    public int getAmountPerUnit() {
        return amountPerUnit;
    }

    public boolean contains(AEKey key) {
        return key != null && key.getType() == this;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
