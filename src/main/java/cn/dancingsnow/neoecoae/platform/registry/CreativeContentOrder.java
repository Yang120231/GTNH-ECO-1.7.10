package cn.dancingsnow.neoecoae.platform.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Display order only: content remains registered under its original identities and numeric IDs. */
@SideOnly(Side.CLIENT)
public final class CreativeContentOrder {

    private static final Map<String, Integer> FAMILIES = new LinkedHashMap<>();
    private static final Pattern TIER = Pattern.compile("^(.*)_(?:l([0-9]+)|([0-9]+)m)$");

    static {
        families(
            "storage_system",
            "storage_casing",
            "storage_interface",
            "eco_drive",
            "energy_cell",
            "storage_vent",
            "eco_item_storage_cell",
            "eco_fluid_storage_cell",
            "eco_infinite_item_cell",
            "eco_cell_component",
            "eco_infinite_cell_component",
            "eco_item_cell_housing",
            "eco_fluid_cell_housing");
        families(
            "crafting_system",
            "crafting_casing",
            "crafting_interface",
            "crafting_pattern_bus",
            "crafting_worker",
            "fx_monitor_core",
            "crafting_parallel_core",
            "input_hatch",
            "output_hatch",
            "crafting_vent",
            "crafting_network_switch",
            "crafting_high_energy_network_switch");
        families(
            "computation_system",
            "computation_casing",
            "computation_interface",
            "computation_transmitter",
            "computation_drive",
            "computation_threading_core",
            "computation_parallel_core",
            "computation_cooling_controller",
            "eco_computation_cell",
            "computation_network_switch",
            "computation_high_energy_network_switch");
        families(
            "integrated_working_station",
            "large_integrated_working_station_casing",
            "large_integrated_working_station_interface",
            "large_integrated_working_station_input_hatch",
            "large_integrated_working_station_output_hatch");
        families(
            "iron_dust",
            "aluminum_ore",
            "raw_aluminum_ore",
            "raw_aluminum_block",
            "aluminum_ingot",
            "aluminum_dust",
            "aluminum_block",
            "tungsten_ore",
            "raw_tungsten_ore",
            "raw_tungsten_block",
            "tungsten_ingot",
            "tungsten_dust",
            "tungsten_block",
            "aluminum_alloy_ingot",
            "aluminum_alloy_dust",
            "aluminum_alloy_block",
            "aluminum_alloy_casing",
            "black_tungsten_alloy_ingot",
            "black_tungsten_alloy_dust",
            "black_tungsten_alloy_block",
            "black_tungsten_alloy_casing",
            "energized_crystal",
            "energized_crystal_dust",
            "energized_crystal_block",
            "flawless_budding_energized_crystal",
            "flawed_budding_energized_crystal",
            "chipped_budding_energized_crystal",
            "damaged_budding_energized_crystal",
            "small_energized_crystal_bud",
            "medium_energized_crystal_bud",
            "large_energized_crystal_bud",
            "energized_crystal_cluster",
            "energized_fluix_crystal",
            "energized_fluix_crystal_dust",
            "energized_fluix_crystal_block",
            "crystal_ingot",
            "crystal_matrix",
            "energized_superconductive_ingot",
            "energized_superconductive_block",
            "cryotheum",
            "cryotheum_crystal",
            "superconducting_processor_press",
            "superconducting_processor_print",
            "superconducting_processor");
        families(
            "aluminum_sword",
            "aluminum_pickaxe",
            "aluminum_axe",
            "aluminum_shovel",
            "aluminum_hoe",
            "tungsten_sword",
            "tungsten_pickaxe",
            "tungsten_axe",
            "tungsten_shovel",
            "tungsten_hoe",
            "aluminum_alloy_upgrade_smithing_template",
            "aluminum_alloy_sword",
            "aluminum_alloy_pickaxe",
            "aluminum_alloy_axe",
            "aluminum_alloy_shovel",
            "aluminum_alloy_hoe",
            "black_tungsten_alloy_upgrade_smithing_template",
            "black_tungsten_alloy_sword",
            "black_tungsten_alloy_pickaxe",
            "black_tungsten_alloy_axe",
            "black_tungsten_alloy_shovel",
            "black_tungsten_alloy_hoe");
    }

    private CreativeContentOrder() {}

    private static void families(String... ids) {
        for (String id : ids) {
            if (FAMILIES.put(id, FAMILIES.size()) != null)
                throw new IllegalStateException("Duplicate creative family: " + id);
        }
    }

    public static String id(ItemStack stack) {
        String registered = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        return registered == null ? "" : registered;
    }

    public static void sort(List<ItemStack> stacks) {
        stacks.sort((first, second) -> compare(id(first), id(second)));
    }

    private static int compare(String first, String second) {
        String firstPath = first.startsWith("neoecoae:") ? first.substring("neoecoae:".length()) : first;
        String secondPath = second.startsWith("neoecoae:") ? second.substring("neoecoae:".length()) : second;
        var firstTier = TIER.matcher(firstPath);
        var secondTier = TIER.matcher(secondPath);
        boolean firstHasTier = firstTier.matches(), secondHasTier = secondTier.matches();
        String firstFamily = firstHasTier ? firstTier.group(1) : firstPath;
        String secondFamily = secondHasTier ? secondTier.group(1) : secondPath;
        int family = Integer.compare(
            FAMILIES.getOrDefault(firstFamily, Integer.MAX_VALUE),
            FAMILIES.getOrDefault(secondFamily, Integer.MAX_VALUE));
        if (family != 0) return family;
        if (firstHasTier && secondHasTier) {
            int tier = Integer.compare(
                Integer.parseInt(firstTier.group(2) == null ? firstTier.group(3) : firstTier.group(2)),
                Integer.parseInt(secondTier.group(2) == null ? secondTier.group(3) : secondTier.group(2)));
            if (tier != 0) return tier;
        }
        return first.compareTo(second);
    }
}
