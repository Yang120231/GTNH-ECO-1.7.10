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
        register("coal", TestItems.COAL);
        register("iron_pickaxe", TestItems.IRON_PICKAXE);
        register("raw_iron", TestItems.RAW_IRON);
        register("raw_copper", TestItems.RAW_COPPER);
        register("copper_ingot", TestItems.COPPER_INGOT);
        register("gold_ingot", TestItems.GOLD_INGOT);
        register("brick", TestItems.BRICK);
        register("bucket", TestItems.BUCKET);
        register("charcoal", TestItems.CHARCOAL);
        register("clay_ball", TestItems.CLAY_BALL);
        register("emerald", TestItems.EMERALD);
        register("end_stone", TestItems.END_STONE);
        register("flint", TestItems.FLINT);
        register("honey_bottle", TestItems.HONEY_BOTTLE);
        register("iron_block", TestItems.IRON_BLOCK);
        register("iron_nugget", TestItems.IRON_NUGGET);
        register("netherite_upgrade_smithing_template", TestItems.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        register("netherrack", TestItems.NETHERRACK);
        register("obsidian", TestItems.OBSIDIAN);
        register("paper", TestItems.PAPER);
        register("sugar", TestItems.SUGAR);
        register("water_bucket", TestItems.WATER_BUCKET);
    }

    private void register(String path, Item item) {
        String id = "minecraft:" + path;
        byName.put(id, item);
        byIdentity.put(item, id);
    }

    public static void initialize() {
        if (ITEM.byName.size() != 31) throw new IllegalStateException("Test identity registry incomplete");
    }

    public ResourceLocation getKey(Item item) {
        return ResourceLocation.parse(Objects.requireNonNull(byIdentity.get(item)));
    }

    public ResourceLocation findKey(Item item) {
        String id = byIdentity.get(item);
        return id == null ? null : ResourceLocation.parse(id);
    }

    public Optional<Item> getOptional(ResourceLocation id) {
        return Optional.ofNullable(byName.get(id.toString()));
    }
}
