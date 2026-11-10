package cn.dancingsnow.neoecoae.platform.ae2.storage;

/** Implemented on rv3's NetworkInventoryHandler by a mixin. */
public interface NativeNetworkReads {

    /** Server-thread monitor revision, including invalidations that do not post an item delta. */
    interface Listing {

        long neoecoae$listingVersion();

        /** False means that this reader needs a full snapshot (rebuild or bounded history expired). */
        boolean neoecoae$listingChangesSince(long version,
            java.util.Set<cn.dancingsnow.neoecoae.platform.ae2.key.AEKey> changed);
    }

    /**
     * Whether any mounted handler reads another network (a storage bus on an interface). rv3 de-duplicates such
     * subnetworks only while listing, so the same subnet change can be reported through several buses.
     */
    boolean neoecoae$readsOtherNetwork();

    /** Changes whenever an insertion is accepted by crafting rather than physical storage. */
    long neoecoae$craftingInsertionVersion();
}
