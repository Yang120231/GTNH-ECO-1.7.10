package cn.dancingsnow.neoecoae.platform.ae2.storage;

/** Explicit unwrapping contract; native wrappers expose the inventory they actually delegate to. */
public interface DelegatingStorage {

    MEStorage neoecoae$getDelegate();
}
