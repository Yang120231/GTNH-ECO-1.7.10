package cn.dancingsnow.neoecoae.crafting.runtime;

public interface ECOCraftingBatchTransaction {

    int craftCount();

    void commit();

    void rollback();
}
