package cn.dancingsnow.neoecoae.impl.storage;

/** Equal versions and view identities guarantee an unchanged visible inventory listing. */
public interface StorageListingVersion {

    long UNKNOWN = Long.MIN_VALUE;

    long neoecoae$listingVersion();

    default Object neoecoae$listingIdentity() {
        return this;
    }
}
