package cn.dancingsnow.neoecoae.platform.ae2.storage;

@FunctionalInterface
public interface ISaveProvider {

    void saveChanges();

    default void notifyPersistence() {
        saveChanges();
    }

    /**
     * True only for hosts that call {@code ECOCellMutationBatch.flushDeferred} before their cell stack is read,
     * replaced, dropped, unloaded or saved; such hosts receive at most one cell write per server tick.
     */
    default boolean defersCellPersistence() {
        return false;
    }
}
