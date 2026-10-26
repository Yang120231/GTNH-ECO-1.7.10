package cn.dancingsnow.neoecoae.platform.gt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;
import cn.dancingsnow.neoecoae.platform.recipe.WorkstationRecipes;
import cn.dancingsnow.neoecoae.platform.registry.WorkstationMachines;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Explicit live-machine acceptance. Recipes run through AE2's real device ticks and machine inventory. */
public final class NativeWorkstationAcceptance {

    private static final int X = 112, Y = 240, Z = 112;
    private List<Map.Entry<ResourceLocation, IntegratedWorkingStationRecipe>> recipes;
    private int index, stage;
    private long started, deadline;
    private ECOIntegratedWorkingStationBlockEntity machine;
    private double beforePower;
    private boolean nbtChecked;

    public static void register() {
        if (Boolean.getBoolean("neoecoae.workstationAcceptance")) cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new NativeWorkstationAcceptance());
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)
            || event.world.provider.dimensionId != 0
            || stage == 3) return;
        WorldServer world = (WorldServer) event.world;
        try {
            if (stage == 0) {
                recipes = new ArrayList<>(
                    WorkstationRecipes.getAll()
                        .entrySet());
                require(recipes.size() == 30, "30 actual source workstation recipes");
                world.setBlock(X, Y, Z, WorkstationMachines.WORKSTATION, 2, 3);
                world.setBlock(
                    X + 1,
                    Y,
                    Z,
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .energyCellCreative()
                        .maybeBlock()
                        .get(),
                    0,
                    3);
                machine = (ECOIntegratedWorkingStationBlockEntity) world.getTileEntity(X, Y, Z);
                require(machine != null, "Real registered workstation tile");
                world.getChunkFromBlockCoords(X, Z)
                    .setChunkModified();
                stage = 1;
                deadline = world.getTotalWorldTime() + 200;
                return;
            }
            if (stage == 1) {
                var node = machine.getGridNode();
                if (node == null || !node.isActive()) {
                    require(world.getTotalWorldTime() <= deadline, "AE2 power/channel ready before deadline");
                    return;
                }
                setup(world);
                stage = 2;
                return;
            }
            var entry = recipes.get(index);
            var recipe = entry.getValue();
            if (!nbtChecked && machine.getProcessingTime() >= 20) {
                var saved = new net.minecraft.nbt.NBTTagCompound();
                machine.writeToNBT(saved);
                var restored = (ECOIntegratedWorkingStationBlockEntity) net.minecraft.tileentity.TileEntity
                    .createAndLoadEntity(saved);
                require(restored != null, "Native registered tile ID reload");
                int progress = restored.getProcessingTime();
                require(
                    progress == machine.getProcessingTime() && restored.getTask() != null
                        && restored.getProcessingTime() == progress,
                    "Recipe progress survives native NBT reload");
                require(restored.getAECurrentPower() == machine.getAECurrentPower(), "Native power NBT reload");
                for (int slot = 0; slot < 10; slot++) require(
                    ItemStack.areItemStacksEqual(restored.getStackInSlot(slot), machine.getStackInSlot(slot)),
                    "Native inventory NBT reload");
                nbtChecked = true;
            }
            require(world.getTotalWorldTime() <= deadline, "Recipe processing timed out: " + entry.getKey());
            ItemStack item = machine.getOutputInv()
                .getStackInSlot(0);
            FluidStack fluid = machine.getOutputTank()
                .getFluid();
            if (recipe.hasItemOutput() && item == null || recipe.hasFluidOutput() && fluid == null) return;
            if (recipe.hasItemOutput()) require(
                ItemStack.areItemStacksEqual(item, recipe.itemOutput()),
                "Actual item output: " + entry.getKey());
            if (recipe.hasFluidOutput()) require(
                fluid.isFluidEqual(recipe.fluidOutput()) && fluid.amount == recipe.fluidOutput().amount,
                "Actual fluid output: " + entry.getKey());
            for (int slot = 0; slot < 9; slot++) require(
                machine.getInputInv()
                    .getStackInSlot(slot) == null,
                "Inputs consumed exactly once: " + entry.getKey());
            require(
                machine.getInputTank()
                    .getFluidAmount() == 0,
                "Fluid input consumed exactly: " + entry.getKey());
            double paid = beforePower - machine.getAECurrentPower();
            require(paid > 0 && paid <= recipe.energy() + .01, "Real native AE debit: " + entry.getKey());
            long elapsed = world.getTotalWorldTime() - started;
            if (index == 0) require(elapsed >= recipe.processingTicks(), "Unupgraded source processing duration");
            else require(elapsed <= 15, "Four native speed cards accelerate actual processing");
            NeoECOAE.LOG.info(
                "NEOECOAE_WORKSTATION_RECIPE_OK id={} ticks={} paid_ae={} output_item={} output_mb={}",
                entry.getKey(),
                elapsed,
                paid,
                item == null ? 0 : item.stackSize,
                fluid == null ? 0 : fluid.amount);
            index++;
            if (index >= recipes.size()) {
                stage = 3;
                NeoECOAE.LOG.info(
                    "NEOECOAE_WORKSTATION_ACCEPTANCE_OK recipes={} real_ticks=true native_ae=true speed_cards=true",
                    index);
                world.getChunkFromBlockCoords(X, Z)
                    .setChunkModified();
                if (Boolean.getBoolean("neoecoae.workstationAcceptanceStopAfter")) MinecraftServer.getServer()
                    .initiateShutdown();
            } else setup(world);
        } catch (Throwable failure) {
            stage = 3;
            NeoECOAE.LOG.error("NEOECOAE_WORKSTATION_ACCEPTANCE_FAILED index=" + index, failure);
            MinecraftServer.getServer()
                .initiateShutdown();
        }
    }

    private void setup(WorldServer world) {
        var recipe = recipes.get(index)
            .getValue();
        for (int i = 0; i < 10; i++) machine.setInventorySlotContents(i, null);
        machine.clearFluid();
        machine.clearFluidOut();
        machine.setAutoExport(false);
        for (int slot = 0; slot < machine.getUpgrades()
            .getSizeInventory(); slot++)
            machine.getUpgrades()
                .setInventorySlotContents(
                    slot,
                    index == 0 ? null
                        : AEApi.instance()
                            .definitions()
                            .materials()
                            .cardSpeed()
                            .maybeStack(1)
                            .get());
        int slot = 0;
        for (var input : recipe.inputItems()) {
            ItemStack stack = input.ingredient()
                .examples()
                .get(0);
            stack.stackSize = input.count();
            machine.getInputInv()
                .setStackInSlot(slot++, stack);
        }
        if (!recipe.inputFluid()
            .ingredient()
            .isEmpty()) {
            FluidStack fluid = recipe.inputFluid()
                .ingredient()
                .examples(
                    recipe.inputFluid()
                        .amount())
                .get(0);
            fluid.amount = recipe.inputFluid()
                .amount();
            require(machine.fill(ForgeDirection.UP, fluid, true) == fluid.amount, "Native input tank capacity");
        }
        machine.injectAEPower(machine.getAEMaxPower(), Actionable.MODULATE);
        beforePower = machine.getAECurrentPower();
        started = world.getTotalWorldTime();
        deadline = started + 300;
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
