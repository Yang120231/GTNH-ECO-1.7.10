package cn.dancingsnow.neoecoae.all;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.neoecoae.NeoECOAE;

public final class NECreativeTabs {

    private static final Map<String, Integer> TAB_ORDER = createTabOrder();

    public static final CreativeTabs NEO_ECO_AE = new CreativeTabs("neoecoae") {

        @Override
        public Item getTabIconItem() {
            return NEItems.aluminumIngot;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public void displayAllReleventItems(List itemList) {
            super.displayAllReleventItems(itemList);
            itemList.sort(new Comparator<ItemStack>() {

                @Override
                public int compare(ItemStack left, ItemStack right) {
                    int order = Integer.compare(getSortOrder(left), getSortOrder(right));
                    if (order != 0) {
                        return order;
                    }

                    int name = getRegistryName(left).compareTo(getRegistryName(right));
                    if (name != 0) {
                        return name;
                    }

                    return Integer.compare(left.getItemDamage(), right.getItemDamage());
                }
            });
        }
    };

    private NECreativeTabs() {}

    private static int getSortOrder(ItemStack stack) {
        Integer order = TAB_ORDER.get(getRegistryName(stack));
        return order != null ? order.intValue() : Integer.MAX_VALUE;
    }

    private static String getRegistryName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null ? name : "";
    }

    private static Map<String, Integer> createTabOrder() {
        Map<String, Integer> order = new HashMap<String, Integer>();

        add(order, "aluminum_ore");
        add(order, "tungsten_ore");
        add(order, "raw_aluminum_block");
        add(order, "raw_tungsten_block");
        add(order, "aluminum_block");
        add(order, "tungsten_block");
        add(order, "aluminum_alloy_block");
        add(order, "black_tungsten_alloy_block");
        add(order, "energized_crystal_block");
        add(order, "energized_superconductive_block");
        add(order, "energized_fluix_crystal_block");

        add(order, "aluminum_alloy_casing");
        add(order, "black_tungsten_alloy_casing");
        add(order, "storage_casing");
        add(order, "storage_controller_l4");
        add(order, "storage_controller_l6");
        add(order, "storage_controller_l9");
        add(order, "storage_vent");
        add(order, "storage_interface");
        add(order, "eco_drive");

        add(order, "crafting_casing");
        add(order, "crafting_controller_l4");
        add(order, "crafting_controller_l6");
        add(order, "crafting_controller_l9");
        add(order, "input_hatch");
        add(order, "output_hatch");
        add(order, "crafting_vent");
        add(order, "crafting_pattern_bus");
        add(order, "crafting_worker");
        add(order, "crafting_interface");

        add(order, "computation_casing");
        add(order, "computation_controller_l4");
        add(order, "computation_controller_l6");
        add(order, "computation_controller_l9");
        add(order, "computation_cooling_controller_l4");
        add(order, "computation_cooling_controller_l6");
        add(order, "computation_cooling_controller_l9");
        add(order, "computation_drive");
        add(order, "computation_interface");
        add(order, "computation_transmitter");

        add(order, "raw_aluminum_ore");
        add(order, "aluminum_ingot");
        add(order, "aluminum_dust");
        add(order, "raw_tungsten_ore");
        add(order, "tungsten_ingot");
        add(order, "tungsten_dust");
        add(order, "aluminum_alloy_ingot");
        add(order, "aluminum_alloy_dust");
        add(order, "black_tungsten_alloy_ingot");
        add(order, "black_tungsten_alloy_dust");

        add(order, "aluminum_sword");
        add(order, "aluminum_pickaxe");
        add(order, "aluminum_axe");
        add(order, "aluminum_shovel");
        add(order, "aluminum_hoe");
        add(order, "tungsten_sword");
        add(order, "tungsten_pickaxe");
        add(order, "tungsten_axe");
        add(order, "tungsten_shovel");
        add(order, "tungsten_hoe");
        add(order, "aluminum_alloy_sword");
        add(order, "aluminum_alloy_pickaxe");
        add(order, "aluminum_alloy_axe");
        add(order, "aluminum_alloy_shovel");
        add(order, "aluminum_alloy_hoe");
        add(order, "black_tungsten_alloy_sword");
        add(order, "black_tungsten_alloy_pickaxe");
        add(order, "black_tungsten_alloy_axe");
        add(order, "black_tungsten_alloy_shovel");
        add(order, "black_tungsten_alloy_hoe");

        return order;
    }

    private static void add(Map<String, Integer> order, String id) {
        order.put(NeoECOAE.MODID + ":" + id, Integer.valueOf(order.size()));
    }
}
