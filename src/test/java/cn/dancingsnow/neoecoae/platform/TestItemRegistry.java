package cn.dancingsnow.neoecoae.platform;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.item.Item;

import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

public final class TestItemRegistry {

    public static final TestItemRegistry ITEM = new TestItemRegistry();
    public static final cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider CONTEXT = new cn.dancingsnow.neoecoae.platform.minecraft.HolderLookup.Provider() {

        @Override
        public ResourceLocation itemKey(Item item) {
            return ITEM.getKey(item);
        }

        @Override
        public Item item(ResourceLocation id) {
            return ITEM.getOptional(id)
                .orElse(null);
        }

        @Override
        public net.minecraftforge.fluids.Fluid fluid(String name) {
            return name.equals("water") ? TestFluids.WATER : name.equals("lava") ? TestFluids.LAVA : null;
        }
    };
    private final Map<String, Item> byName = new HashMap<>();
    private final Map<Item, String> byIdentity = new IdentityHashMap<>();

    private TestItemRegistry() {
        register("stone", TestItems.STONE);
        register("dirt", TestItems.DIRT);
        register("cobblestone", TestItems.COBBLESTONE);
        register("iron_ingot", TestItems.IRON_INGOT);
        register("glass_bottle", TestItems.GLASS_BOTTLE);
        register("sand", TestItems.SAND);
        register("gravel", TestItems.GRAVEL);
        register("glass", TestItems.GLASS);
        register("diamond", TestItems.DIAMOND);
    }

    private void register(String path, Item item) {
        String id = "minecraft:" + path;
        byName.put(id, item);
        byIdentity.put(item, id);
    }

    public static void initialize() {
        if (ITEM.byName.size() != 9) throw new IllegalStateException("Test identity registry incomplete");
    }

    public ResourceLocation getKey(Item item) {
        return ResourceLocation.parse(Objects.requireNonNull(byIdentity.get(item)));
    }

    public Optional<Item> getOptional(ResourceLocation id) {
        return Optional.ofNullable(byName.get(id.toString()));
    }
}
