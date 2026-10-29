package cn.dancingsnow.neoecoae.impl.storage;

public enum ECOStorageInterfaceMode {

    STORAGE("storage"),
    INPUT("input"),
    OUTPUT("output");

    private final String serializedName;

    ECOStorageInterfaceMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }
}
