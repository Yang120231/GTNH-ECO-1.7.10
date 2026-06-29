package cn.dancingsnow.neoecoae.storage.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import cn.dancingsnow.neoecoae.storage.item.IECOStorageMatrixItem;
import cn.dancingsnow.neoecoae.storage.item.ECOStorageCellMetadata;

public class ECOCellHandler implements ICellHandler {

    public static final ECOCellHandler INSTANCE = new ECOCellHandler();

    private ECOCellHandler() {}

    @Override
    public boolean isCell(ItemStack is) {
        return is != null && is.getItem() instanceof IECOStorageMatrixItem && !ECOStorageCellMetadata.hasNonPortableState(is);
    }

    @Override
    public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider host, StorageChannel channel) {
        if (!this.isCell(is) || channel == null) {
            return null;
        }
        return new ECOCellInventoryHandler(is, host, channel);
    }

    @Override
    public IIcon getTopTexture_Light() {
        return null;
    }

    @Override
    public IIcon getTopTexture_Medium() {
        return null;
    }

    @Override
    public IIcon getTopTexture_Dark() {
        return null;
    }

    @Override
    public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler,
        IMEInventoryHandler inv, ItemStack is, StorageChannel chan) {}

    @Override
    public int getStatusForCell(ItemStack is, IMEInventory handler) {
        if (!(handler instanceof ECOCellInventoryHandler)) {
            return 0;
        }
        ECOCellInventoryHandler ecoHandler = (ECOCellInventoryHandler) handler;
        int types = ecoHandler.getBackend()
            .snapshot()
            .getTypeCount();
        if (types == 0) {
            return 1;
        }
        long remaining = ecoHandler.getBackend()
            .getCapacityPolicy()
            .isInfinite() ? Long.MAX_VALUE
                : ecoHandler.getBackend()
                    .getCapacityPolicy()
                    .getRemaining(ecoHandler.getBackend().getUsed())
                    .toLongSaturated();
        return remaining > 0L ? 2 : 4;
    }

    @Override
    public double cellIdleDrain(ItemStack is, IMEInventory handler) {
        if (is != null && is.getItem() instanceof IECOStorageMatrixItem) {
            return Math.max(0.5D, ((IECOStorageMatrixItem) is.getItem()).getDisplayBytes(is) / 1048576.0D);
        }
        return 0.5D;
    }
}
