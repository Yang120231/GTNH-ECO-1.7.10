package cn.dancingsnow.neoecoae.platform.registry;

import java.util.Optional;

import net.minecraft.item.Item;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

public final class BuiltInRegistries {

    public static final ItemRegistry ITEM = new ItemRegistry();

    private BuiltInRegistries() {}

    public static final class ItemRegistry {

        public ResourceLocation getKey(Item item) {
            return ResourceLocation.parse((String) Item.itemRegistry.getNameForObject(item));
        }

        public Optional<Item> getOptional(ResourceLocation id) {
            return Optional.ofNullable((Item) Item.itemRegistry.getObject(id.toString()));
        }
    }
}
