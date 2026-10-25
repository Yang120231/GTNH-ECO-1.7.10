package cn.dancingsnow.neoecoae.platform.ae2.storage;

@FunctionalInterface
public interface IStorageMounts {

    void mount(MEStorage inventory, int priority);
}
