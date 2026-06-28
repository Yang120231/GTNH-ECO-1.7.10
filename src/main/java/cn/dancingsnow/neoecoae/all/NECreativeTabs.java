package cn.dancingsnow.neoecoae.all;

import java.util.Comparator;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class NECreativeTabs {

    private static final Comparator<ItemStack> TAB_COMPARATOR = (left, right) -> {
        int order = Integer.compare(getSortOrder(left), getSortOrder(right));
        if (order != 0) {
            return order;
        }

        int name = getRegistryName(left).compareTo(getRegistryName(right));
        if (name != 0) {
            return name;
        }

        return Integer.compare(left.getItemDamage(), right.getItemDamage());
    };

    public static final CreativeTabs NEO_ECO_AE = new CreativeTabs("neoecoae") {

        @Override
        public Item getTabIconItem() {
            return NEItems.aluminumIngot;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public void displayAllReleventItems(List itemList) {
            super.displayAllReleventItems(itemList);
            itemList.sort(TAB_COMPARATOR);
        }
    };

    private NECreativeTabs() {}

    private static int getSortOrder(ItemStack stack) {
        String name = getLocalRegistryName(stack);
        return getGroupOrder(name) * 1000 + getItemOrder(name);
    }

    private static String getRegistryName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null ? name : "";
    }

    private static String getLocalRegistryName(ItemStack stack) {
        String name = getRegistryName(stack);
        int separator = name.indexOf(':');
        return separator >= 0 ? name.substring(separator + 1) : name;
    }

    private static int getGroupOrder(String name) {
        if (name.endsWith("_ore") || name.startsWith("raw_")
            || name.endsWith("_block")
            || name.startsWith("energized_")) {
            return 0;
        }
        if (name.startsWith("storage_") || "eco_drive".equals(name)) {
            return 1;
        }
        if (name.startsWith("crafting_") || "input_hatch".equals(name) || "output_hatch".equals(name)) {
            return 2;
        }
        if (name.startsWith("computation_")) {
            return 3;
        }
        if (name.endsWith("_ingot") || name.endsWith("_dust")) {
            return 4;
        }
        if (isTool(name)) {
            return 5;
        }
        if ("debug".equals(name)) {
            return 8;
        }
        return 9;
    }

    private static int getItemOrder(String name) {
        if (name.endsWith("_ore")) {
            return 0;
        }
        if (name.startsWith("raw_") && name.endsWith("_block")) {
            return 10;
        }
        if (name.endsWith("_block")) {
            return 20;
        }
        if (name.endsWith("_casing")) {
            return 100;
        }
        if (name.contains("_system_l4")) {
            return 200;
        }
        if (name.contains("_system_l6")) {
            return 210;
        }
        if (name.contains("_system_l9")) {
            return 220;
        }
        if (name.contains("_cooling_controller_l4")) {
            return 230;
        }
        if (name.contains("_cooling_controller_l6")) {
            return 240;
        }
        if (name.contains("_cooling_controller_l9")) {
            return 250;
        }
        if (name.endsWith("_vent")) {
            return 300;
        }
        if (name.endsWith("_interface")) {
            return 310;
        }
        if (name.startsWith("energy_cell_")) {
            return 315;
        }
        if (name.endsWith("_drive")) {
            return 320;
        }
        if ("input_hatch".equals(name)) {
            return 330;
        }
        if ("output_hatch".equals(name)) {
            return 340;
        }
        if (name.endsWith("_pattern_bus")) {
            return 350;
        }
        if (name.endsWith("_worker")) {
            return 360;
        }
        if (name.contains("_parallel_core_")) {
            return 370;
        }
        if (name.contains("_threading_core_")) {
            return 380;
        }
        if (name.startsWith("raw_")) {
            return 400;
        }
        if (name.endsWith("_ingot")) {
            return 410;
        }
        if (name.endsWith("_dust")) {
            return 420;
        }
        return isTool(name) ? getToolOrder(name) : 900;
    }

    private static boolean isTool(String name) {
        return name.endsWith("_sword") || name.endsWith("_pickaxe")
            || name.endsWith("_axe")
            || name.endsWith("_shovel")
            || name.endsWith("_hoe");
    }

    private static int getToolOrder(String name) {
        if (name.endsWith("_sword")) {
            return 500;
        }
        if (name.endsWith("_pickaxe")) {
            return 510;
        }
        if (name.endsWith("_axe")) {
            return 520;
        }
        if (name.endsWith("_shovel")) {
            return 530;
        }
        if (name.endsWith("_hoe")) {
            return 540;
        }
        return 590;
    }
}
