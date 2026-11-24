package cn.dancingsnow.neoecoae.platform.ae2.storage;

@FunctionalInterface
public interface ISaveProvider {

    void saveChanges();

    default void notifyPersistence() {
        saveChanges();
    }

    /**
     * True only for hosts that require a successful {@code ECOCellMutationBatch.flushDeferred} before transferring
     * or saving their cell; such hosts can coalesce writes until the end of the server tick.
     */
    default boolean defersCellPersistence() {
        return false;
    }
}
