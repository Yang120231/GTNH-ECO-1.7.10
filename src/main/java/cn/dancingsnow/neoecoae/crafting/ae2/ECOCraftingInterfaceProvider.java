package cn.dancingsnow.neoecoae.crafting.ae2;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.util.ScheduledReason;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.tile.TileCraftingPatternBus;
import cn.dancingsnow.neoecoae.tile.TileECOController;

final class ECOCraftingInterfaceProvider implements ICraftingProvider {

    private TileECOController controller;
    private boolean active;
    private ScheduledReason scheduledReason = ScheduledReason.UNDEFINED;

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
            bus.provideCrafting(craftingTracker, this);
        }
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (!this.active || this.controller == null || !this.controller.isFormed()) {
            this.scheduledReason = ScheduledReason.NO_TARGET;
            return false;
        }
        boolean accepted = TileCraftingPatternBus.pushPattern(this.controller, patternDetails, table);
        this.scheduledReason = accepted ? ScheduledReason.UNDEFINED : ScheduledReason.SOMETHING_STUCK;
        return accepted;
    }

    @Override
    public boolean isBusy() {
        return !this.active || this.controller == null
            || !this.controller.isFormed()
            || !this.controller.hasVirtualCraftingCapacity();
    }

    @Override
    public ItemStack getCrafterIcon() {
        return new ItemStack(NEBlocks.craftingInterface);
    }

    @Override
    public ScheduledReason getScheduledReason() {
        return this.scheduledReason;
    }
}
