package cn.dancingsnow.neoecoae.crafting.runtime;

import net.minecraft.inventory.InventoryCrafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import cn.dancingsnow.neoecoae.tile.TileECOController;

public interface ECOCraftingBatchCoordinator {

    ECOCraftingBatchTransaction prepareBatch(ICraftingPatternDetails details, InventoryCrafting table,
        TileECOController controller);

    void recordSlowCraftAccepted();

    void handleBatchFailure(RuntimeException failure);

    boolean isBatchDispatchSuspended();
}
