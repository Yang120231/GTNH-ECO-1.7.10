package cn.dancingsnow.neoecoae.crafting.ae2;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;

final class ECOCraftingInterfaceProvider implements ICraftingProvider {

    private TileECOController controller;
    private boolean active;

    void configure(TileECOController controller, boolean active) {
        this.controller = controller;
        this.active = active;
    }

    void clear() {
        this.controller = null;
        this.active = false;
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        if (!this.active || this.controller == null || !this.controller.isFormed()) {
            return;
        }
        for (TileCraftingPatternBus bus : this.controller.getCraftingPatternBuses()) {
            bus.provideCrafting(craftingTracker);
        }
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (!this.active || this.controller == null || !this.controller.isFormed()) {
            return false;
        }
        for (TileCraftingPatternBus bus : this.controller.getCraftingPatternBuses()) {
            if (bus.pushPattern(patternDetails, table)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        return !this.active || this.controller == null
            || !this.controller.isFormed()
            || this.controller.findAvailableCraftingWorker() == null;
    }

    @Override
    public ItemStack getCrafterIcon() {
        return new ItemStack(NEBlocks.craftingInterface);
    }
}
