package cn.dancingsnow.neoecoae.crafting.ae2;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import cn.dancingsnow.neoecoae.tile.TileECOController;
import cn.dancingsnow.neoecoae.tile.TileECOInterface;

public final class ECOCraftingAe2Registration {

    private final TileECOInterface host;
    private final ECOCraftingInterfaceProvider provider = new ECOCraftingInterfaceProvider();

    private ICraftingGrid attachedGrid;
    private IGridNode attachedNode;
    private TileECOController attachedController;
    private int attachedControllerRevision = -1;
    private boolean attachedActive;
    private boolean directlyRegistered;
    private boolean fallbackEnabled;

    public ECOCraftingAe2Registration(TileECOInterface host) {
        this.host = host;
    }

    public void refresh(ICraftingGrid grid, IGridNode node, TileECOController controller, boolean active,
        boolean forcePatternRefresh) {
        if (grid == null || node == null || controller == null || !controller.isFormed()) {
            this.detach();
            return;
        }

        int controllerRevision = controller.getStorageBackendRevision();
        boolean changed = this.attachedGrid != grid || this.attachedNode != node
            || this.attachedController != controller
            || this.attachedControllerRevision != controllerRevision
            || this.attachedActive != active;
        if (!changed) {
            if (forcePatternRefresh && this.isRegistered()) {
                this.postPatternChange(this.directlyRegistered ? this.provider : this.host);
            }
            return;
        }

        if (this.directlyRegistered && this.attachedGrid != null && this.attachedGrid != grid) {
            this.unregisterDirect();
        }

        this.attachedGrid = grid;
        this.attachedNode = node;
        this.attachedController = controller;
        this.attachedControllerRevision = controllerRevision;
        this.attachedActive = active;
        this.provider.configure(controller, active);

        this.directlyRegistered = ECOCraftingProviderBridge.register(grid, this.provider);
        this.fallbackEnabled = !this.directlyRegistered;
        this.postPatternChange(this.directlyRegistered ? this.provider : this.host);
    }

    public void detach() {
        boolean hadFallback = this.fallbackEnabled;
        if (this.directlyRegistered) {
            this.unregisterDirect();
        } else if (hadFallback) {
            this.postPatternChange(this.host);
        }
        this.attachedGrid = null;
        this.attachedNode = null;
        this.attachedController = null;
        this.attachedControllerRevision = -1;
        this.attachedActive = false;
        this.fallbackEnabled = false;
        this.provider.clear();
    }

    public boolean isRegistered() {
        return this.directlyRegistered || this.fallbackEnabled;
    }

    public void provideFallbackCrafting(ICraftingProviderHelper craftingTracker) {
        if (this.fallbackEnabled) {
            this.provider.provideCrafting(craftingTracker);
        }
    }

    public boolean pushFallbackPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        return this.fallbackEnabled && this.provider.pushPattern(patternDetails, table);
    }

    public boolean isFallbackBusy() {
        return !this.fallbackEnabled || this.provider.isBusy();
    }

    public ItemStack getCrafterIcon() {
        return this.provider.getCrafterIcon();
    }

    private void unregisterDirect() {
        ICraftingGrid grid = this.attachedGrid;
        if (grid != null && ECOCraftingProviderBridge.unregister(grid, this.provider)) {
            this.postPatternChange(this.provider);
        }
        this.directlyRegistered = false;
    }

    private void postPatternChange(ICraftingProvider changedProvider) {
        if (this.attachedNode == null) {
            return;
        }
        this.attachedNode.getGrid()
            .postEvent(new MENetworkCraftingPatternChange(changedProvider, this.attachedNode));
    }
}
