package cn.dancingsnow.neoecoae.api.storage;

import java.util.Set;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellWorkbenchItem;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public interface IBasicECOCellItem extends ICellWorkbenchItem, IECOStorageCellItem {

    StorageTierDescriptor getTier();

    AEKeyType getKeyType();

    long getBytes();

    long getBytesPerType();

    default double getIdleDrain() {
        return (double) getBytes() / (1 << 20);
    }

    int getTotalTypes();

    ECOCellType getCellType();

    @Override
    default Set<AEKeyType> getKeyTypes() {
        return cn.dancingsnow.neoecoae.platform.jdk.JdkCompat.setOf(getKeyType());
    }

    default boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return false;
    }
}
