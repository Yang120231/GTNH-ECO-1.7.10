package cn.dancingsnow.neoecoae.platform.ae2.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.tile.storage.TileDrive;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.platform.registry.NativeStorageCellItem;

/** One handler is registered with both the native AE2 registry and the imported ECO extension registry. */
public final class NativeCellHandler implements ICellHandler, IECOCellHandler {

    @Override
    public boolean isCell(ItemStack stack) {
        return stack != null && stack.getItem() instanceof NativeStorageCellItem;
    }

    @Override
    public IECOStorageCell getCellInventory(ItemStack stack, ISaveProvider host) {
        return isCell(stack) ? ((NativeStorageCellItem) stack.getItem()).createCellInventory(stack, host) : null;
    }

    @Override
    public IMEInventoryHandler getCellInventory(ItemStack stack, appeng.api.storage.ISaveProvider host,
        StorageChannel channel) {
        // Old saves may already contain ECO cells in native drives. Keep them removable but unmounted.
        // The native IO port requests inventories with a null host, so transfers remain supported.
        if (host instanceof TileDrive) return null;
        if (!isCell(stack) || !((NativeStorageCellItem) stack.getItem()).getKeyTypes()
            .stream()
            .anyMatch(type -> type.nativeChannel() == channel)) return null;
        return new NativeStorageInventory<>(stack, host, channel);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public IIcon getTopTexture_Light() {
        return appeng.client.texture.ExtraBlockTextures.BlockMEChestItems_Light.getIcon();
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public IIcon getTopTexture_Medium() {
        return appeng.client.texture.ExtraBlockTextures.BlockMEChestItems_Medium.getIcon();
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public IIcon getTopTexture_Dark() {
        return appeng.client.texture.ExtraBlockTextures.BlockMEChestItems_Dark.getIcon();
    }

    @Override
    public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler handler,
        IMEInventoryHandler inventory, ItemStack stack, StorageChannel channel) {
        appeng.util.Platform.openGUI(
            player,
            (net.minecraft.tileentity.TileEntity) chest,
            chest.getUp(),
            appeng.core.sync.GuiBridge.GUI_ME);
    }

    @Override
    public int getStatusForCell(ItemStack stack, IMEInventory inventory) {
        if (!(inventory instanceof NativeStorageInventory)) return 0;
        switch (((NativeStorageInventory<?>) inventory).business()
            .getStatus()) {
            case EMPTY:
                return 1;
            case NOT_EMPTY:
                return 2;
            case TYPES_FULL:
                return 3;
            case FULL:
                return 4;
            default:
                return 0;
        }
    }

    @Override
    public double cellIdleDrain(ItemStack stack, IMEInventory inventory) {
        return ((NativeStorageCellItem) stack.getItem()).getIdleDrain();
    }
}
