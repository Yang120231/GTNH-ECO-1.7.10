package cn.dancingsnow.neoecoae.compat.nei;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.Tags;
import cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Discovered by NEI on the client after native recipes have been installed. */
public final class NEINeoECOAEConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        for (var kind : WorkstationRecipeHandler.Kind.values()) {
            var handler = new WorkstationRecipeHandler(kind);
            API.registerRecipeHandler(handler);
            API.registerUsageHandler(handler);
            API.addRecipeCatalyst(new ItemStack(WorkstationMachines.WORKSTATION), handler);
            if (kind != WorkstationRecipeHandler.Kind.INTEGRATED) {
                API.addRecipeCatalyst(
                    new ItemStack(WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_CASING.value()),
                    handler);
                API.addRecipeCatalyst(
                    new ItemStack(WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.value()),
                    handler);
                API.addRecipeCatalyst(
                    new ItemStack(WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH.value()),
                    handler);
                API.addRecipeCatalyst(
                    new ItemStack(WorkstationMachines.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH.value()),
                    handler);
            }
        }
        API.hideItem(new ItemStack(cn.dancingsnow.neoecoae.platform.minecraft.FluidDisplayItem.INSTANCE));
        MinecraftForge.EVENT_BUS.register(this);
        // NEI's first handler-info event precedes plugin loading; also install the initial entries.
        registerHandlerInfo(new NEIRegisterHandlerInfosEvent());
    }

    @SubscribeEvent
    public void registerHandlerInfo(NEIRegisterHandlerInfosEvent event) {
        for (var kind : WorkstationRecipeHandler.Kind.values()) event.registerHandlerInfo(
            new HandlerInfo.Builder(kind.id(), getName(), NeoECOAE.MODID)
                .setDisplayStack(new ItemStack(WorkstationMachines.WORKSTATION))
                .setWidth(166)
                .setHeight(kind.height())
                // Pattern transfer applies to machine recipes; coolant entries are not patternable.
                .setShowOverlayButton(kind != WorkstationRecipeHandler.Kind.COOLING)
                .build());
    }

    @Override
    public String getName() {
        return "Neo ECO AE Extension";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
