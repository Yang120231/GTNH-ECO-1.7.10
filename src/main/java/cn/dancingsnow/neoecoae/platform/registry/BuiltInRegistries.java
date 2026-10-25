package cn.dancingsnow.neoecoae.platform.registry;

import java.util.Optional;

import net.minecraft.item.Item;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

public final class BuiltInRegistries {

    public static final ItemRegistry ITEM = new ItemRegistry();
    private static volatile java.util.function.Function<Item, ResourceLocation> itemNames = item -> {
        String id = (String) Item.itemRegistry.getNameForObject(item);
        return id == null ? null : ResourceLocation.parse(id);
    };

    private BuiltInRegistries() {}

    /** Read-only identity query injection for isolated platform tests; native FML registration is untouched. */
    public static void useItemNameLookup(java.util.function.Function<Item, ResourceLocation> lookup) {
        itemNames = java.util.Objects.requireNonNull(lookup);
    }

    public static final class ItemRegistry {

        public ResourceLocation getKey(Item item) {
            return itemNames.apply(item);
        }

        public Optional<Item> getOptional(ResourceLocation id) {
            return Optional.ofNullable((Item) Item.itemRegistry.getObject(id.toString()));
        }
    }
}
