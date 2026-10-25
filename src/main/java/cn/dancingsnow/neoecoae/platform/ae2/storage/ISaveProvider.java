package cn.dancingsnow.neoecoae.platform.ae2.storage;

@FunctionalInterface
public interface ISaveProvider {

    void saveChanges();

    default void notifyPersistence() {
        saveChanges();
    }
}
