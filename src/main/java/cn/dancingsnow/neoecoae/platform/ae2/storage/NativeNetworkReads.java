package cn.dancingsnow.neoecoae.platform.ae2.storage;

/** Implemented on rv3's NetworkInventoryHandler by a mixin. */
public interface NativeNetworkReads {

    /**
     * Whether any mounted handler reads another network (a storage bus on an interface). rv3 de-duplicates such
     * subnetworks only while listing, so the same subnet change can be reported through several buses.
     */
    boolean neoecoae$readsOtherNetwork();
}
