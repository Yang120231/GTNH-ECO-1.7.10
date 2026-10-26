package cn.dancingsnow.neoecoae.platform.recipe;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import cpw.mods.fml.common.registry.GameRegistry;

/** Explicit modern identifier to GTNH definition conversion. No placeholder ingredients. */
public final class SourceItems {

    private static final Map<String, IItemDefinition> AE = new LinkedHashMap<>();

    private SourceItems() {}

    public static void initialize() {
        var definitions = AEApi.instance()
            .definitions();
        var b = definitions.blocks();
        var m = definitions.materials();
        var p = definitions.parts();
        AE.put("256k_crafting_storage", b.craftingStorage256k());
        AE.put("advanced_card", m.advCard());
        AE.put("calculation_processor", m.calcProcessor());
        AE.put("calculation_processor_press", m.calcProcessorPress());
        AE.put("cell_component_256k", m.cell256kPart());
        AE.put("cell_workbench", b.cellWorkbench());
        AE.put("charged_certus_quartz_crystal", m.certusQuartzCrystalCharged());
        AE.put("charger", b.charger());
        AE.put("condenser", b.condenser());
        AE.put("controller", b.controller());
        AE.put("crafting_accelerator", b.craftingAccelerator());
        AE.put("crafting_monitor", b.craftingMonitor());
        AE.put("crafting_unit", b.craftingUnit());
        AE.put("dense_energy_cell", b.energyCellDense());
        AE.put("drive", b.drive());
        AE.put("engineering_processor", m.engProcessor());
        AE.put("engineering_processor_press", m.engProcessorPress());
        AE.put("export_bus", p.exportBus());
        AE.put("import_bus", p.importBus());
        AE.put("inscriber", b.inscriber());
        AE.put("interface", b.iface());
        // GTNH's interface is both the pattern provider and inventory interface.
        AE.put("pattern_provider", b.iface());
        AE.put("logic_processor", m.logicProcessor());
        AE.put("logic_processor_press", m.logicProcessorPress());
        AE.put("molecular_assembler", b.molecularAssembler());
        AE.put("printed_silicon", m.siliconPrint());
        AE.put("quartz_block", b.quartz());
        AE.put("quartz_vibrant_glass", b.quartzVibrantGlass());
        AE.put("singularity", m.singularity());
        AE.put("sky_stone_block", b.skyStone());
        AE.put("storage_monitor", p.storageMonitor());
    }

    public static ItemStack stack(String id, int count) {
        if (count <= 0) throw new IllegalArgumentException("Invalid source count: " + count);
        if (id.equals("minecraft:blue_ice"))
            return new ItemStack(cn.dancingsnow.neoecoae.platform.registry.RecipeMaterials.BLUE_ICE, count);
        if (id.startsWith("ae2:")) {
            var definition = AE.get(id.substring(4));
            return definition == null ? null
                : definition.maybeStack(count)
                    .orNull();
        }
        int colon = id.indexOf(':');
        if (colon < 1) throw new IllegalArgumentException("Invalid source item id: " + id);
        Item item = GameRegistry.findItem(id.substring(0, colon), id.substring(colon + 1));
        return item == null ? null : new ItemStack(item, count);
    }
}
