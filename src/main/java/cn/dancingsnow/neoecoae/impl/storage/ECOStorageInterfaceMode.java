package cn.dancingsnow.neoecoae.impl.storage;

public enum ECOStorageInterfaceMode implements cn.dancingsnow.neoecoae.platform.minecraft.StringRepresentable {

    STORAGE("storage"),
    INPUT("input"),
    OUTPUT("output");

    private final String serializedName;

    ECOStorageInterfaceMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
