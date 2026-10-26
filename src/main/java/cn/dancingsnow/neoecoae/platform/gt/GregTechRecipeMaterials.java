package cn.dancingsnow.neoecoae.platform.gt;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;

/** Explicit GTM identities resolved to the pinned GTNH API. Never manufactures replacement items or fluids. */
final class GregTechRecipeMaterials {

    private GregTechRecipeMaterials() {}

    static ItemStack item(String id, int count) {
        switch (id) {
            case "gtceu:ev_machine_hull":
                return ItemList.Hull_EV.get(count);
            case "gtceu:ev_electric_motor":
                return ItemList.Electric_Motor_EV.get(count);
            case "gtceu:ev_electric_pump":
                return ItemList.Electric_Pump_EV.get(count);
            case "gtceu:ev_conveyor_module":
                return ItemList.Conveyor_Module_EV.get(count);
            case "gtceu:ev_robot_arm":
                return ItemList.Robot_Arm_EV.get(count);
            case "gtceu:ev_emitter":
                return ItemList.Emitter_EV.get(count);
            case "gtceu:ev_sensor":
                return ItemList.Sensor_EV.get(count);
            case "gtceu:ev_field_generator":
                return ItemList.Field_Generator_EV.get(count);
            case "gtceu:iv_emitter":
                return ItemList.Emitter_IV.get(count);
            case "gtceu:iv_sensor":
                return ItemList.Sensor_IV.get(count);
            case "gtceu:iv_field_generator":
                return ItemList.Field_Generator_IV.get(count);
            case "gtceu:zpm_field_generator":
                return ItemList.Field_Generator_ZPM.get(count);
            case "gtceu:aluminium_ingot":
                return material(OrePrefixes.ingot, Materials.Aluminium, count);
            case "gtceu:aluminium_dust":
                return material(OrePrefixes.dust, Materials.Aluminium, count);
            case "gtceu:aluminium_block":
                return material(OrePrefixes.block, Materials.Aluminium, count);
            case "gtceu:aluminium_plate":
                return material(OrePrefixes.plate, Materials.Aluminium, count);
            case "gtceu:tungsten_ingot":
                return material(OrePrefixes.ingot, Materials.Tungsten, count);
            case "gtceu:tungsten_dust":
                return material(OrePrefixes.dust, Materials.Tungsten, count);
            case "gtceu:tungsten_block":
                return material(OrePrefixes.block, Materials.Tungsten, count);
            case "gtceu:stainless_steel_plate":
                return material(OrePrefixes.plate, Materials.StainlessSteel, count);
            case "gtceu:titanium_plate":
                return material(OrePrefixes.plate, Materials.Titanium, count);
            case "gtceu:naquadah_alloy_plate":
                return material(OrePrefixes.plate, Materials.NaquadahAlloy, count);
            case "gtceu:polyethylene_plate":
                return material(OrePrefixes.plate, Materials.Polyethylene, count);
            case "gtceu:fine_gold_wire":
                return material(OrePrefixes.wireFine, Materials.Gold, count);
            case "gtceu:quartzite_dust":
                return material(OrePrefixes.dust, Materials.Quartzite, count);
            case "gtceu:sodium_dust":
                return material(OrePrefixes.dust, Materials.Sodium, count);
            case "gtceu:silicon_ingot":
                return material(OrePrefixes.ingot, Materials.Silicon, count);
            case "gtceu:silicon_dust":
                return material(OrePrefixes.dust, Materials.Silicon, count);
            case "gtceu:niobium_titanium_ingot":
                return material(OrePrefixes.ingot, Materials.NiobiumTitanium, count);
            case "gtceu:vanadium_gallium_ingot":
                return material(OrePrefixes.ingot, Materials.VanadiumGallium, count);
            case "gtceu:yttrium_barium_cuprate_ingot":
                return material(OrePrefixes.ingot, Materials.YttriumBariumCuprate, count);
            // GTM marks this tag member optional. GTNH has no identical material; retain the other three choices.
            case "gtceu:mercury_barium_calcium_cuprate_ingot":
                return null;
            default:
                return null;
        }
    }

    private static ItemStack material(OrePrefixes prefix, Materials material, int count) {
        return GTOreDictUnificator.get(prefix, material, count);
    }

    static Fluid fluid(String id) {
        FluidStack stack;
        switch (id) {
            case "gtceu:argon":
                stack = Materials.Argon.getGas(1);
                break;
            case "gtceu:distilled_water":
                stack = GTModHandler.getDistilledWater(1);
                break;
            case "gtceu:soldering_alloy":
                stack = Materials.SolderingAlloy.getMolten(1);
                break;
            default:
                return null;
        }
        return stack == null ? null : stack.getFluid();
    }
}
