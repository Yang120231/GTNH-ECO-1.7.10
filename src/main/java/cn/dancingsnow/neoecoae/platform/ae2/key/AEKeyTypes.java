package cn.dancingsnow.neoecoae.platform.ae2.key;

public final class AEKeyTypes {

    private AEKeyTypes() {}

    public static java.util.List<AEKeyType> getAll() {
        return AEKeyType.registeredTypes();
    }

    public static AEKeyType get(cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation id) {
        for (var type : getAll()) if (type.getId()
            .equals(id)) return type;
        return null;
    }
}
