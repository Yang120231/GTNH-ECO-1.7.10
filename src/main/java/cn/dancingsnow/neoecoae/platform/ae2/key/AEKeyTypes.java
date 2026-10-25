package cn.dancingsnow.neoecoae.platform.ae2.key;

public final class AEKeyTypes {

    private AEKeyTypes() {}

    public static java.util.List<AEKeyType> getAll() {
        return AEKeyType.registeredTypes();
    }
}
