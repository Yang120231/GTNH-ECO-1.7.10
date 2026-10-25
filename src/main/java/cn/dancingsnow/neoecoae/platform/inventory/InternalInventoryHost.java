package cn.dancingsnow.neoecoae.platform.inventory;

public interface InternalInventoryHost {

    void saveChangedInventory(AppEngInternalInventory inventory);

    default void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        saveChangedInventory(inventory);
    }
}
