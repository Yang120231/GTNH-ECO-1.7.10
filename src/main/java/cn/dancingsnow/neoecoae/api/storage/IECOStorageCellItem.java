package cn.dancingsnow.neoecoae.api.storage;

import java.util.Set;

import cn.dancingsnow.neoecoae.platform.ae2.key.AEKeyType;
import cn.dancingsnow.neoecoae.platform.registry.StorageTierDescriptor;

public interface IECOStorageCellItem {

    StorageTierDescriptor getTier();

    ECOCellType getCellType();

    Set<AEKeyType> getKeyTypes();
}
