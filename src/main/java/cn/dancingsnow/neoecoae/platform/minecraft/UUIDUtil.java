package cn.dancingsnow.neoecoae.platform.minecraft;

public final class UUIDUtil {

    private UUIDUtil() {}

    public static int[] uuidToIntArray(java.util.UUID id) {
        long high = id.getMostSignificantBits(), low = id.getLeastSignificantBits();
        return new int[] { (int) (high >> 32), (int) high, (int) (low >> 32), (int) low };
    }
}
