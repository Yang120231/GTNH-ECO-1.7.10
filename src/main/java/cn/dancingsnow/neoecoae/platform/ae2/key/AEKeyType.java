package cn.dancingsnow.neoecoae.platform.ae2.key;

import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Resource identities and amount units shared by native storage and planning. */
public final class AEKeyType {

    private static final java.util.Map<ResourceLocation, AEKeyType> TYPES = new java.util.LinkedHashMap<>();
    private static final java.util.Map<IAEStackType<?>, AEKeyType> NATIVE_TYPES = new java.util.IdentityHashMap<>();

    private static final AEKeyType ITEMS = new AEKeyType("i", StorageChannel.ITEMS, 8, 1);
    private static final AEKeyType FLUIDS = new AEKeyType("f", StorageChannel.FLUIDS, 8000, 1000);
    private final ResourceLocation id;
    private final StorageChannel channel;
    private final IAEStackType<?> stackType;
    private final int amountPerByte, amountPerUnit;

    private AEKeyType(String id, StorageChannel channel, int bytes, int units) {
        this(ResourceLocation.fromNamespaceAndPath("ae2", id), channel, bytes, units);
    }

    private AEKeyType(ResourceLocation id, StorageChannel channel, int bytes, int units) {
        this(id, channel, null, bytes, units);
    }

    private AEKeyType(ResourceLocation id, StorageChannel channel, IAEStackType<?> stackType, int bytes, int units) {
        this.id = id;
        this.channel = channel;
        this.stackType = stackType;
        amountPerByte = bytes;
        amountPerUnit = units;
        TYPES.put(id, this);
        if (stackType != null) NATIVE_TYPES.putIfAbsent(stackType, this);
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

    /** All addons register their native types during preInit, before ECO's init and any save is read. */
    public static void registerNativeTypes() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) fromNative(type);
    }

    static synchronized AEKeyType fromNative(IAEStackType<?> stackType) {
        java.util.Objects.requireNonNull(stackType);
        if (stackType == AEItemStackType.ITEM_STACK_TYPE) return ITEMS;
        if (stackType == AEFluidStackType.FLUID_STACK_TYPE) return FLUIDS;
        AEKeyType known = NATIVE_TYPES.get(stackType);
        if (known != null) return known;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ae2", "native/" + stackType.getId());
        if (TYPES.containsKey(id) || stackType.getAmountPerByte() < 1 || stackType.getAmountPerUnit() < 1)
            throw new IllegalArgumentException("Invalid/duplicate native resource type: " + id);
        AEKey.registerDecoder(id.toString(), tag -> AENativeKey.read(stackType, tag));
        return new AEKeyType(id, null, stackType, stackType.getAmountPerByte(), stackType.getAmountPerUnit());
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
        if (channel == null && stackType != null)
            return cn.dancingsnow.neoecoae.platform.minecraft.Component.literal(stackType.getDisplayName());
        return cn.dancingsnow.neoecoae.platform.minecraft.Component.translatable(
            channel == StorageChannel.ITEMS ? "gui.appliedenergistics2.Items" : "gui.appliedenergistics2.Fluids");
    }

    public StorageChannel nativeChannel() {
        return channel;
    }

    public IAEStackType<?> nativeType() {
        // Do not initialize AE2's stack factories while ECO's built-in types are being constructed.
        return channel == StorageChannel.ITEMS ? AEItemStackType.ITEM_STACK_TYPE
            : channel == StorageChannel.FLUIDS ? AEFluidStackType.FLUID_STACK_TYPE : stackType;
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
